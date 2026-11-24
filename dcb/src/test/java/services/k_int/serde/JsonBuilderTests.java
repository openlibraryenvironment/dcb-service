package services.k_int.serde;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

class JsonBuilderTests {
	@Test
	void buildsAnArrayOfAStringANumberAndAnObject() {
		final var json = JsonBuilder.array(array -> array
			.add(value -> value.str("Test"))
			.add(value -> value.num(1.5))
			.add(value -> value.obj(obj -> obj.key("key", val -> val.NULL()))));

		assertThat(json.isArray(), is(true));
		assertThat(json.size(), is(3));
		assertThat(json.get(0).getStringValue(), is("Test"));
		assertThat(json.get(1).getNumberValue().doubleValue(), is(1.5));
		assertThat(json.get(2).get("key").isNull(), is(true));
	}
}
