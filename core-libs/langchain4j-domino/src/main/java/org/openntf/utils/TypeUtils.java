package org.openntf.utils;

import java.util.Collection;

public class TypeUtils {

        /**
         * Check if a string is null or empty.
         * <p>
         * null -> true / "" -> true / " " -> false
         * <p>
         *
         * @param value the string to check
         * @return true if the string is null or blank
         */
        public static boolean isEmpty(String value) {
            return value == null || value.isEmpty();
        }

        public static boolean isNotEmpty(String value) {
            return !isEmpty(value);
        }

        public static boolean isEmpty(Collection<?> collection) {
            return collection == null || collection.isEmpty();
        }

        public static boolean isNotEmpty(Collection<?> collection) {
            return !isEmpty(collection);
        }

}
