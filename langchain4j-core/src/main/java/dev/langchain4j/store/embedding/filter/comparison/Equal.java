package dev.langchain4j.store.embedding.filter.comparison;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.MetadataFilter;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.filter.comparison.NumberComparator.compareAsBigDecimals;
import static dev.langchain4j.store.embedding.filter.comparison.TypeChecker.ensureTypesAreCompatible;

@ToString
@EqualsAndHashCode
public class Equal implements MetadataFilter {

    private final String key;
    private final Object comparisonValue;

    public Equal(String key, Object comparisonValue) {
        this.key = ensureNotBlank(key, "key");
        this.comparisonValue = ensureNotNull(comparisonValue, "comparisonValue with key '" + key + "'");
    }

    public String key() {
        return key;
    }

    public Object comparisonValue() {
        return comparisonValue;
    }


    // TODO move this logic to InMemoryEmbeddingStore and keep these classes as DTOs?
    @Override
    public boolean test(Metadata metadata) {
        if (!metadata.containsKey(key)) {
            return false;
        }

        Object actualValue = metadata.getObject(key);
        ensureTypesAreCompatible(actualValue, comparisonValue, key);

        if (actualValue instanceof Number) {
            return compareAsBigDecimals(actualValue, comparisonValue) == 0;
        }

        return actualValue.equals(comparisonValue);
    }
}