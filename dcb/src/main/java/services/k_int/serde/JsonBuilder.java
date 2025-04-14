package services.k_int.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.json.tree.JsonNode;

public class JsonBuilder {
	
  @NonNull
	public static JsonNode obj(Consumer<JsonObjApi> json) {
		return JsonValueApi.INSTANCE.obj(json);
	}

  @NonNull
	public static JsonNode array( Consumer<JsonArrayApi> json ) {
		return JsonValueApi.INSTANCE.array(json);
	}
	
	public static class JsonObjApi {
		private final Map<String, JsonNode> delegate;
		protected static JsonObjApi from(Map<String, JsonNode> delegate) {
			return new JsonObjApi(delegate);
		}
		
		private JsonObjApi(Map<String, JsonNode> delegate) {
			this.delegate = delegate;
		}
		
		public JsonObjApi key (String key, Function<JsonValueApi, JsonNode> json) {			
			delegate.put(key, json.apply(JsonValueApi.INSTANCE));
			return this;
		}
	}
	
	static class JsonArrayApi {
		private final List<JsonNode> delegate;
		public JsonArrayApi(List<JsonNode> delegate) {
			this.delegate = delegate;
		}
		
		protected static JsonArrayApi from(List<JsonNode> delegate) {
			return new JsonArrayApi(delegate);
		}
		
		public JsonArrayApi add (Function<JsonValueApi, JsonNode> json) {			
			delegate.add(json.apply( JsonValueApi.INSTANCE ));
			return this;
		}
	}
	
	public static class JsonValueApi {
		
		protected static final JsonValueApi INSTANCE = new JsonValueApi();
		
		@NonNull
    public JsonNode NULL() {
        return JsonNode.nullNode();
    }

    @NonNull
		public JsonNode array( Consumer<JsonArrayApi> json ) {
			final List<JsonNode> arrDelegate = new ArrayList<>();
			json.accept( JsonArrayApi.from(arrDelegate) );
			return JsonNode.createArrayNode(arrDelegate);
		}

    /**
     * @param nodes The nodes in this object. Must not be modified after this method is called.
     * @return The immutable array node.
     */
    @NonNull
    public JsonNode obj(Consumer<JsonObjApi> json) {
			final Map<String, JsonNode> objDelegate = new HashMap<>();
			json.accept( JsonObjApi.from(objDelegate) );
			return JsonNode.createObjectNode(objDelegate);
    }

    /**
     * @param value The value of the node.
     * @return A json node representing the given boolean value.
     */
    @NonNull
    public JsonNode bool(boolean value) {
    	return JsonNode.createBooleanNode(value);
    }

    /**
     * @param value The value of the node.
     * @return A json node representing the given string value.
     */
    @NonNull
    public JsonNode str(@NonNull String value) {
    	return JsonNode.createStringNode(value);
    }

    /**
     * @param value The value of the node.
     * @return A json node representing the given numeric value.
     */
    @NonNull
    public JsonNode num(int value) {
      return JsonNode.createNumberNodeImpl(value);
    }

    /**
     * @param value The value of the node.
     * @return A json node representing the given numeric value.
     */
    @NonNull
    public JsonNode num(long value) {
      return JsonNode.createNumberNodeImpl(value);
    }

    /**
     * @param value The value of the node.
     * @return A json node representing the given numeric value.
     */
    @NonNull
    public JsonNode num(@NonNull BigDecimal value) {
      return JsonNode.createNumberNodeImpl(value);
    }

    /**
     * @param value The value of the node.
     * @return A json node representing the given numeric value.
     */
    @NonNull
    public JsonNode num(float value) {
      return JsonNode.createNumberNodeImpl(value);
    }

    /**
     * @param value The value of the node.
     * @return A json node representing the given numeric value.
     */
    @NonNull
    public JsonNode num(double value) {
      return JsonNode.createNumberNodeImpl(value);
    }

    /**
     * @param value The value of the node.
     * @return A json node representing the given numeric value.
     */
    @NonNull
    public JsonNode num(@NonNull BigInteger value) {
      return JsonNode.createNumberNodeImpl(value);
    }
	}
}
