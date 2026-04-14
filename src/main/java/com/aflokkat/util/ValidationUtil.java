package com.aflokkat.util;

/**
 * Utilitaires de validation
 */
public class ValidationUtil {
    
    private ValidationUtil() {
        // Classe utilitaire
    }
    
    /**
     * Valide qu'une string n'est pas null ou vide
     */
    public static void requireNonEmpty(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " ne peut pas être null ou vide");
        }
    }
    
    /**
     * Valide qu'un nombre est positif
     */
    public static void requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " doit être positif, reçu: " + value);
        }
    }
    
    /**
     * Validate qu'un fieldName ne contient pas de caractères dangereux
     */
    public static void validateFieldName(String fieldName) {
        requireNonEmpty(fieldName, "fieldName");
        if (!fieldName.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("fieldName contient des caractères invalides: " + fieldName);
        }
    }
}
