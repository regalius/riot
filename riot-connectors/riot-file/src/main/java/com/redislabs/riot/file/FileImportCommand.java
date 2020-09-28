package com.redislabs.riot.file;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.file.DefaultBufferedReaderFactory;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.separator.DefaultRecordSeparatorPolicy;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.file.transform.Range;
import org.springframework.batch.item.json.JacksonJsonObjectReader;
import org.springframework.batch.item.json.builder.JsonItemReaderBuilder;
import org.springframework.batch.item.redis.support.KeyValue;
import org.springframework.batch.item.xml.XmlObjectReader;
import org.springframework.batch.item.xml.builder.XmlItemReaderBuilder;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.redislabs.riot.AbstractImportCommand;
import com.redislabs.riot.Transfer;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Option;

@Slf4j
@CommandLine.Command(name = "import", aliases = { "i" }, description = "Import file(s)")
@SuppressWarnings({ "rawtypes", "unchecked" })
public class FileImportCommand extends AbstractImportCommand {

	@CommandLine.Parameters(arity = "1..*", description = "File path or URL", paramLabel = "FILE")
	private List<String> files = new ArrayList<>();
	@CommandLine.Mixin
	private final FileOptions fileOptions = new FileOptions();
	@Option(names = "--key-values", description = "File contains encoded Redis key/values")
	private boolean keyValues;
	@CommandLine.Option(names = {
			"--skip" }, description = "Lines to skip at start of file (default: ${DEFAULT-VALUE})", paramLabel = "<count>")
	private int linesToSkip = 0;
	@CommandLine.Option(names = "--include", arity = "1..*", description = "Field indices to include (0-based)", paramLabel = "<index>")
	private int[] includedFields = new int[0];
	@CommandLine.Option(names = "--ranges", arity = "1..*", description = "Fixed-width column ranges", paramLabel = "<int>")
	private Range[] columnRanges = new Range[0];
	@CommandLine.Option(names = { "-q",
			"--quote" }, description = "Escape character (default: ${DEFAULT-VALUE})", paramLabel = "<char>")
	private Character quoteCharacter = DelimitedLineTokenizer.DEFAULT_QUOTE_CHARACTER;

	@Override
	protected ItemProcessor processor() {
		if (keyValues) {
			return new JsonKeyValueItemProcessor();
		}
		return mapProcessor();
	}

	@Override
	protected List<Transfer<Object, Object>> transfers() throws Exception {
		List<Transfer<Object, Object>> transfers = new ArrayList<>();
		for (String file : expandedFileList()) {
			transfers.add(transfer(file));
		}
		return transfers;
	}

	private List<String> expandedFileList() {
		List<String> fileList = new ArrayList<>();
		for (String file : files) {
			if (fileOptions.isFile(file)) {
				Path path = Paths.get(file);
				if (Files.exists(path)) {
					fileList.add(file);
				} else {
					// Path might be glob pattern
					Path parent = path.getParent();
					try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent,
							path.getFileName().toString())) {
						stream.forEach(p -> fileList.add(p.toString()));
					} catch (IOException e) {
						log.debug("Could not list files in {}", path, e);
					}
				}
			} else {
				fileList.add(file);
			}
		}
		return fileList;
	}

	private Transfer transfer(String file) throws Exception {
		FileType fileType = fileOptions.fileType(file);
		Resource resource = fileOptions.inputResource(file);
		String transferName = "Importing " + fileOptions.fileName(resource);
		switch (fileType) {
		case DELIMITED:
			FlatFileItemReaderBuilder delimitedReaderBuilder = flatFileReaderBuilder(resource);
			FlatFileItemReaderBuilder.DelimitedBuilder delimitedBuilder = delimitedReaderBuilder.delimited();
			delimitedBuilder.delimiter(fileOptions.delimiter(file));
			delimitedBuilder.includedFields(includedFields());
			delimitedBuilder.quoteCharacter(quoteCharacter);
			String[] fieldNames = fileOptions.getNames();
			if (fileOptions.isHeader()) {
				BufferedReader reader = new DefaultBufferedReaderFactory().create(resource, fileOptions.getEncoding());
				DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
				tokenizer.setDelimiter(fileOptions.delimiter(file));
				tokenizer.setQuoteCharacter(quoteCharacter);
				if (includedFields.length > 0) {
					tokenizer.setIncludedFields(includedFields);
				}
				fieldNames = tokenizer.tokenize(reader.readLine()).getValues();
				log.debug("Found header {}", Arrays.asList(fieldNames));
			}
			if (fieldNames == null || fieldNames.length == 0) {
				throw new IOException("No fields specified");
			}
			delimitedBuilder.names(fieldNames);
			return transfer(transferName, delimitedReaderBuilder.build(), SourceType.STRING_MAP);
		case FIXED:
			FlatFileItemReaderBuilder fixedReaderBuilder = flatFileReaderBuilder(resource);
			FlatFileItemReaderBuilder.FixedLengthBuilder fixedLength = fixedReaderBuilder.fixedLength();
			Assert.notEmpty(columnRanges, "Column ranges are required");
			fixedLength.columns(columnRanges);
			fixedLength.names(fileOptions.getNames());
			return transfer(transferName, fixedReaderBuilder.build(), SourceType.STRING_MAP);
		case JSON:
			JsonItemReaderBuilder jsonReaderBuilder = new JsonItemReaderBuilder();
			jsonReaderBuilder.name("json-file-reader");
			jsonReaderBuilder.resource(resource);
			JacksonJsonObjectReader jsonObjectReader = new JacksonJsonObjectReader<>(
					keyValues ? KeyValue.class : Map.class);
			jsonObjectReader.setMapper(new ObjectMapper());
			jsonReaderBuilder.jsonObjectReader(jsonObjectReader);
			return transfer(transferName, jsonReaderBuilder.build(),
					keyValues ? SourceType.KEY_VALUE : SourceType.OBJECT_MAP);
		case XML:
			XmlItemReaderBuilder xmlReaderBuilder = new XmlItemReaderBuilder<>();
			xmlReaderBuilder.name("xml-file-reader");
			xmlReaderBuilder.resource(resource);
			XmlObjectReader xmlObjectReader = new XmlObjectReader<>(keyValues ? KeyValue.class : Map.class);
			xmlObjectReader.setMapper(new XmlMapper());
			xmlReaderBuilder.xmlObjectReader(xmlObjectReader);
			return transfer(transferName, xmlReaderBuilder.build(),
					keyValues ? SourceType.KEY_VALUE : SourceType.OBJECT_MAP);
		}
		throw new IllegalArgumentException("Unknown file type: " + fileType);
	}

	private Integer[] includedFields() {
		Integer[] fields = new Integer[includedFields.length];
		for (int index = 0; index < includedFields.length; index++) {
			fields[index] = includedFields[index];
		}
		return fields;
	}

	private FlatFileItemReaderBuilder<Map<String, String>> flatFileReaderBuilder(Resource resource) {
		FlatFileItemReaderBuilder<Map<String, String>> flatFileReaderBuilder = new FlatFileItemReaderBuilder<>();
		flatFileReaderBuilder.name("flat-file-reader");
		flatFileReaderBuilder.resource(resource);
		flatFileReaderBuilder.encoding(fileOptions.getEncoding());
		flatFileReaderBuilder.linesToSkip(linesToSkip);
		flatFileReaderBuilder.strict(true);
		flatFileReaderBuilder.saveState(false);
		flatFileReaderBuilder.fieldSetMapper(new MapFieldSetMapper());
		flatFileReaderBuilder.recordSeparatorPolicy(new DefaultRecordSeparatorPolicy());
		if (fileOptions.isHeader() && linesToSkip == 0) {
			flatFileReaderBuilder.linesToSkip(1);
		}
		return flatFileReaderBuilder;
	}

}
