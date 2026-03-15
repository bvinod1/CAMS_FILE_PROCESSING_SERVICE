package com.cams.fileprocessing.features.validation.models;

/**
 * Defines the rule for a single column in a validation template.
 *
 * <p>All fields are immutable. Instances are created at template load time and cached.
 *
 * @param name       exact column header name (case-sensitive)
 * @param position   zero-indexed expected position in the header row
 * @param dataType   expected data type for values in this column
 * @param required   if {@code true}, the column must be present; absence is a MISSING_COLUMN error
 * @param allowNull  if {@code false}, empty/blank values are a NULL_NOT_ALLOWED error
 * @param maxLength  optional maximum character length for STRING columns; {@code null} = unlimited
 * @param pattern    optional Java regex the value must match; {@code null} = no pattern check
 */
public record ColumnRule(
        String name,
        int position,
        ColumnDataType dataType,
        boolean required,
        boolean allowNull,
        Integer maxLength,
        String pattern
) {
    /**
     * Convenience constructor for required columns with no pattern or length constraint.
     */
    public static ColumnRule required(String name, int position, ColumnDataType dataType) {
        return new ColumnRule(name, position, dataType, true, false, null, null);
    }
}
