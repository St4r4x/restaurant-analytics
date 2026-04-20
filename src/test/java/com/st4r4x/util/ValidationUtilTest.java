package com.st4r4x.util;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Tests unitaires pour ValidationUtil
 */
public class ValidationUtilTest {

    @Test
    void testRequireNonEmpty_ValidString() {
        // Should not throw for valid non-empty string
        ValidationUtil.requireNonEmpty("valid", "fieldName");
    }

    @Test
    void testRequireNonEmpty_NullString() {
        assertThrows(IllegalArgumentException.class, () ->
            ValidationUtil.requireNonEmpty(null, "fieldName"));
    }

    @Test
    void testRequireNonEmpty_EmptyString() {
        assertThrows(IllegalArgumentException.class, () ->
            ValidationUtil.requireNonEmpty("", "fieldName"));
    }

    @Test
    void testRequireNonEmpty_WhitespaceString() {
        assertThrows(IllegalArgumentException.class, () ->
            ValidationUtil.requireNonEmpty("   ", "fieldName"));
    }

    @Test
    void testRequirePositive_ValidPositive() {
        // Should not throw for positive number
        ValidationUtil.requirePositive(5, "limit");
        ValidationUtil.requirePositive(1, "limit");
        ValidationUtil.requirePositive(100, "limit");
    }

    @Test
    void testRequirePositive_Zero() {
        assertThrows(IllegalArgumentException.class, () ->
            ValidationUtil.requirePositive(0, "limit"));
    }

    @Test
    void testRequirePositive_Negative() {
        assertThrows(IllegalArgumentException.class, () ->
            ValidationUtil.requirePositive(-5, "limit"));
    }

    @Test
    void testValidateFieldName_ValidNames() {
        // Should not throw for valid field names
        ValidationUtil.validateFieldName("cuisine");
        ValidationUtil.validateFieldName("borough");
        ValidationUtil.validateFieldName("field_name");
        ValidationUtil.validateFieldName("field123");
        ValidationUtil.validateFieldName("_private");
    }

    @Test
    void testValidateFieldName_FieldNameWithDash() {
        assertThrows(IllegalArgumentException.class, () ->
            ValidationUtil.validateFieldName("field-name"));
    }

    @Test
    void testValidateFieldName_FieldNameWithDot() {
        assertThrows(IllegalArgumentException.class, () ->
            ValidationUtil.validateFieldName("field.name"));
    }

    @Test
    void testValidateFieldName_FieldNameWithSpace() {
        assertThrows(IllegalArgumentException.class, () ->
            ValidationUtil.validateFieldName("field name"));
    }

    @Test
    void testValidateFieldName_FieldNameWithSpecialChar() {
        assertThrows(IllegalArgumentException.class, () ->
            ValidationUtil.validateFieldName("field$name"));
    }

    @Test
    void testValidateFieldName_EmptyFieldName() {
        assertThrows(IllegalArgumentException.class, () ->
            ValidationUtil.validateFieldName(""));
    }
}
