/*
 * Copyright (c) 2024-2025 Serdar Basegmez
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
