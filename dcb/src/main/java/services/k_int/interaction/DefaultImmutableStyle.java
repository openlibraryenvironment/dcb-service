package services.k_int.interaction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.immutables.value.Value.Style;

@Target({ElementType.PACKAGE, ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
@Style(typeImmutable = "*Impl", typeAbstract = {"*Def", "Abstract*"})
public @interface DefaultImmutableStyle {
}
