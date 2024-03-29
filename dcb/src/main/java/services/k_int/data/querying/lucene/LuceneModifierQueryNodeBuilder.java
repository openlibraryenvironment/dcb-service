package services.k_int.data.querying.lucene;

import java.util.Objects;
import java.util.Optional;

import org.apache.lucene.queryparser.flexible.core.nodes.ModifierQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.ModifierQueryNode.Modifier;

import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import services.k_int.data.querying.JpaQuerySpecificationBuilder;

public class LuceneModifierQueryNodeBuilder<T> implements JpaQuerySpecificationBuilder<T,ModifierQueryNode> {

	@Override
	public QuerySpecification<T> build(ModifierQueryNode mod) throws Exception {
		
		// Grab the statement and process it.
		final Optional<QuerySpecification<T>> spec = this.processQueryNode(mod.getChild())
			.filter(Objects::nonNull)
			.findFirst();
		
		Modifier modifier = mod.getModifier();
		return switch (modifier) {
			case MOD_NONE, MOD_REQ -> spec.orElse(null);
			case MOD_NOT -> spec.map( s -> QuerySpecification.not(s)).orElse(null);
			default -> {
				log.debug("Unsupported modifier {}", modifier);
				yield null;
			}
		};
	}
}
