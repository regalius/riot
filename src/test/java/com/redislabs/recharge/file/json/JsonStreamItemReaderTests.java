package com.redislabs.recharge.file.json;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Test;
import org.springframework.core.io.InputStreamResource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonStreamItemReaderTests {

	public static class TestObject {
		private Integer id;
		private String string;
		private Double doubleValue;
		private Boolean booleanValue;
		private List<TestObject> nestedTestObjectList;

		public void setId(Integer id) {
			this.id = id;
		}

		public Integer getId() {
			return this.id;
		}

		public void getString(String string) {
			this.string = string;
		}

		public String getString() {
			return this.string;
		}

		public void setDoubleValue(Double doubleValue) {
			this.doubleValue = doubleValue;
		}

		public Double getDoubleValue() {
			return this.doubleValue;
		}

		public void setBooleanValue(Boolean booleanValue) {
			this.booleanValue = booleanValue;
		}

		public Boolean getBooleanValue() {
			return this.booleanValue;
		}

		public void setNestedTestObjectList(List<TestObject> nestedTestObjectList) {
			this.nestedTestObjectList = nestedTestObjectList;
		}

		public List<TestObject> getNestedTestObjectList() {
			return this.nestedTestObjectList;
		}
	}

//	private void testTemplate(String resourceString, String keyName) throws Exception {
//		JacksonUnmarshaller unmarshaller = new JacksonUnmarshaller();
//		unmarshaller.setObjectMapper(new ObjectMapper());
//		JsonStreamItemReader<JsonStreamItemReaderTests.TestObject> itemReader = new JsonStreamItemReader<JsonStreamItemReaderTests.TestObject>();
//		itemReader.setResource(new InputStreamResource(ClassLoader.class.getResourceAsStream(resourceString)));
//		itemReader.setTargetClass(JsonStreamItemReaderTests.TestObject.class);
//		itemReader.setUnmarshaller(unmarshaller);
//		itemReader.setKeyName(keyName);
//		itemReader.afterPropertiesSet();
//		itemReader.doOpen();
//
//		TestObject testObject = itemReader.read();
//		assertEquals(new Integer(1), testObject.getId());
//		assertEquals("a", testObject.getString());
//		assertEquals(new Double(0.012), testObject.getDoubleValue());
//		assertEquals(true, testObject.getBooleanValue());
//		List<TestObject> nestedTestObjects = testObject.getNestedTestObjectList();
//		assertEquals(1, nestedTestObjects.size());
//		TestObject nestedTestObject0 = nestedTestObjects.get(0);
//		assertEquals(null, nestedTestObject0.getId());
//		assertEquals("nested-a", nestedTestObject0.getString());
//		assertEquals(null, nestedTestObject0.getDoubleValue());
//		assertEquals(null, nestedTestObject0.getBooleanValue());
//
//		testObject = itemReader.read();
//		assertEquals("\"b", testObject.getString());
//		assertEquals(false, testObject.getBooleanValue());
//		assertEquals(null, testObject.getNestedTestObjectList());
//
//		testObject = itemReader.read();
//		assertEquals(null, testObject.getBooleanValue());
//		assertEquals(null, testObject.getNestedTestObjectList());
//	}

//	@Test
//	public void keyName() throws Exception {
//		testTemplate("/com/redislabs/recharge/file/json/keyName.json", "arrayOfObjects");
//	}
//
//	@Test
//	public void noKeyName() throws Exception {
//		testTemplate("/com/redislabs/recharge/file/json/noKeyName.json", null);
//	}
//
//	@Test
//	public void emptyKeyName() throws Exception {
//		testTemplate("/com/redislabs/recharge/file/json/noKeyName.json", "");
//	}

	@Test
	public void callData() throws Exception {
		JacksonUnmarshaller unmarshaller = new JacksonUnmarshaller();
		unmarshaller.setObjectMapper(new ObjectMapper());
		TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
		};
		JsonStreamItemReader<HashMap<String, Object>> itemReader = new JsonStreamItemReader<HashMap<String, Object>>();
		itemReader.setResource(new InputStreamResource(
				ClassLoader.class.getResourceAsStream("/com/redislabs/recharge/file/json/CallData.json")));
		itemReader.setTargetClass(typeRef);
		itemReader.setUnmarshaller(unmarshaller);
		itemReader.setKeyName("data");
		itemReader.afterPropertiesSet();
		itemReader.doOpen();
		HashMap<String, Object> map = itemReader.read();
		print(null, map);
	}

	private void print(String prefix, Map<String, Object> map) {
		for (Entry<String, Object> entry : map.entrySet()) {
			printValue(getProperty(prefix, entry.getKey()), entry.getValue());
		}
	}

	private void printValue(String property, Object value) {
		if (value instanceof Map) {
			print(property, (Map) value);
		} else {
			if (value instanceof Collection) {
				int index = 0;
				for (Object object : (Collection) value) {
					printValue(property + "[" + index + "]", object);
					index++;
				}
			} else {
				System.out.println(property + "=" + String.valueOf(value));
			}
		}
	}

	private String getProperty(String prefix, String key) {
		if (prefix == null) {
			return key;
		}
		return prefix + "." + key;
	}
}