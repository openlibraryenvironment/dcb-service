package services.k_int.serde;

import org.junit.jupiter.api.Test;


public class JsonBuilderTests {
	
	@Test
	void testJsonBuilder () {
		JsonBuilder.array(json ->
			
			json
				.add(arr ->	arr.str("Test"))
				.add(arr ->	arr.num(1.5))
				.add(arr -> arr.obj(obj ->
					obj.
						key("key", val -> val.NULL())))
		);
		
		
	}
}
