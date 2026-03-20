package com.aflokkat.util;

import org.junit.Test;

/**
 * Tests unitaires pour ValidationUtil
 */
public class ValidationUtilTest {
    
    @Test
    public void testRequireNonEmpty_ValidString() {
        // Should not throw for valid non-empty string
        ValidationUtil.requireNonEmpty("valid", "fieldName");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testRequireNonEmpty_NullString() {
        ValidationUtil.requireNonEmpty(null, "fieldName");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testRequireNonEmpty_EmptyString() {
        ValidationUtil.requireNonEmpty("", "fieldName");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testRequireNonEmpty_WhitespaceString() {
        ValidationUtil.requireNonEmpty("   ", "fieldName");
    }
    
    @Test
    public void testRequirePositive_ValidPositive() {
        // Should not throw for positive number
        ValidationUtil.requirePositive(5, "limit");
        ValidationUtil.requirePositive(1, "limit");
        ValidationUtil.requirePositive(100, "limit");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testRequirePositive_Zero() {
        ValidationUtil.requirePositive(0, "limit");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testRequirePositive_Negative() {
        ValidationUtil.requirePositive(-5, "limit");
    }
    
    @Test
    public void testValidateFieldName_ValidNames() {
        // Should not throw for valid field names
        ValidationUtil.validateFieldName("cuisine");
        ValidationUtil.validateFieldName("borough");
        ValidationUtil.validateFieldName("field_name");
        ValidationUtil.validateFieldName("field123");
        ValidationUtil.validateFieldName("_private");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testValidateFieldName_FieldNameWithDash() {
        ValidationUtil.validateFieldName("field-name");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testValidateFieldName_FieldNameWithDot() {
        ValidationUtil.validateFieldName("field.name");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testValidateFieldName_FieldNameWithSpace() {
        ValidationUtil.validateFieldName("field name");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testValidateFieldName_FieldNameWithSpecialChar() {
        ValidationUtil.validateFieldName("field$name");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testValidateFieldName_EmptyFieldName() {
        ValidationUtil.validateFieldName("");
    }
}
