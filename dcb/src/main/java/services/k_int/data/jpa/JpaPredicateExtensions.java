package services.k_int.data.jpa;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

public interface JpaPredicateExtensions {
	
	public static Predicate ilike(CriteriaBuilder criteriaBuilder, Expression<String> x, Expression<String> pattern) {
  	
  	Expression<String> lx = criteriaBuilder.lower(x);
  	Expression<String> lpattern = criteriaBuilder.lower(pattern);
  	
		return criteriaBuilder.like(lx, lpattern);
	}

	public static Predicate ilike(CriteriaBuilder criteriaBuilder, Expression<String> x, String pattern) {
  	
  	Expression<String> lx = criteriaBuilder.lower(x);
  	String lpattern = pattern != null ? pattern.toLowerCase() : pattern;
  	
		return criteriaBuilder.like(lx, lpattern);
	}
  
	public static Predicate ilike(CriteriaBuilder criteriaBuilder, Expression<String> x, Expression<String> pattern, Expression<Character> escapeChar) {
  	
  	Expression<String> lx = criteriaBuilder.lower(x);
  	Expression<String> lpattern = criteriaBuilder.lower(pattern);
  	
		return criteriaBuilder.like(lx, lpattern, escapeChar);
	}

  public static Predicate ilike(CriteriaBuilder criteriaBuilder, Expression<String> x, Expression<String> pattern, char escapeChar){
  	
  	Expression<String> lx = criteriaBuilder.lower(x);
  	Expression<String> lpattern = criteriaBuilder.lower(pattern);
  	
		return criteriaBuilder.like(lx, lpattern, escapeChar);
	}

  public static Predicate ilike(CriteriaBuilder criteriaBuilder, Expression<String> x, String pattern, Expression<Character> escapeChar) {
  	
  	Expression<String> lx = criteriaBuilder.lower(x);
  	String lpattern = pattern != null ? pattern.toLowerCase() : pattern;
  	
		return criteriaBuilder.like(lx, lpattern, escapeChar);
	}

  public static Predicate ilike(CriteriaBuilder criteriaBuilder, Expression<String> x, String pattern, char escapeChar){
  	
  	Expression<String> lx = criteriaBuilder.lower(x);
  	String lpattern = pattern != null ? pattern.toLowerCase() : pattern;
  	
		return criteriaBuilder.like(lx, lpattern, escapeChar);
	}
}
