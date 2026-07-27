package com.st4r4x.util;

import java.util.regex.Pattern;

/**
 * Utilitaires de validation
 */
public class ValidationUtil {

    private static final Pattern PASSWORD_UPPERCASE = Pattern.compile(".*[A-Z].*");
    private static final Pattern PASSWORD_DIGIT = Pattern.compile(".*[0-9].*");

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
     * Valide qu'un mot de passe respecte la politique de complexité :
     * au moins 10 caractères, une majuscule, un chiffre.
     * Ne s'applique qu'à l'inscription — jamais à la connexion.
     */
    public static void requireValidPassword(String password) {
        requireNonEmpty(password, "password");
        if (password.length() < 10) {
            throw new IllegalArgumentException("password doit contenir au moins 10 caractères");
        }
        if (!PASSWORD_UPPERCASE.matcher(password).matches()) {
            throw new IllegalArgumentException("password doit contenir au moins une majuscule");
        }
        if (!PASSWORD_DIGIT.matcher(password).matches()) {
            throw new IllegalArgumentException("password doit contenir au moins un chiffre");
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
