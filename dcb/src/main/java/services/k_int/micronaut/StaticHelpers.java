package services.k_int.micronaut;

import io.micronaut.context.annotation.Context;
import io.micronaut.core.convert.ConversionService;
import lombok.Getter;

@Getter
@Context
public class StaticHelpers {

	private final ConversionService conversionService;
	
	private static StaticHelpers _singleton;
	
	public static StaticHelpers get() {
		if (_singleton == null) throw new IllegalStateException("StaticHelpers not inialized");
		
		return _singleton;
	}
	
	StaticHelpers(ConversionService conversionService) {
		this.conversionService = conversionService;
		_singleton = this;
	}
}
