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
package org.openntf.langchain4j.data;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MetadataDefinitionTest {

    private List<MetaField> collectFields(MetadataDefinition definition) {
        List<MetaField> list = new ArrayList<>();
        definition.forEachField(list::add);
        return list;
    }

    @Test
    void testEmptyDefinitionHasNoFields() {
        MetadataDefinition def = MetadataDefinition.EMPTY;
        List<MetaField> fields = collectFields(def);
        Assertions.assertTrue(fields.isEmpty(), "EMPTY definition should have no fields");
    }

    @Test
    void testForEachFieldProvidesCorrectFormulaAndType() {
        var def = MetadataDefinition.builder()
                                    .addString("s")
                                    .addString("t", "@T")
                                    .addInteger("i")
                                    .addLong("l", "@L")
                                    .addDouble("d")
                                    .addTemporal("tm", "@TM")
                                    .build();

        List<MetaField> fields = collectFields(def);
        Assertions.assertEquals(6, fields.size());

        Assertions.assertEquals("s", fields.get(0).fieldName());
        Assertions.assertEquals("s", fields.get(0).formula());
        Assertions.assertEquals(String.class, fields.get(0).fieldType());

        Assertions.assertEquals("t", fields.get(1).fieldName());
        Assertions.assertEquals("@T", fields.get(1).formula());
        Assertions.assertEquals(String.class, fields.get(1).fieldType());

        Assertions.assertEquals("i", fields.get(2).fieldName());
        Assertions.assertEquals("i", fields.get(2).formula());
        Assertions.assertEquals(int.class, fields.get(2).fieldType());

        Assertions.assertEquals("l", fields.get(3).fieldName());
        Assertions.assertEquals("@L", fields.get(3).formula());
        Assertions.assertEquals(long.class, fields.get(3).fieldType());

        Assertions.assertEquals("d", fields.get(4).fieldName());
        Assertions.assertEquals("d", fields.get(4).formula());
        Assertions.assertEquals(double.class, fields.get(4).fieldType());

        Assertions.assertEquals("tm", fields.get(5).fieldName());
        Assertions.assertEquals("@TM", fields.get(5).formula());
        Assertions.assertEquals(Temporal.class, fields.get(5).fieldType());
    }

    @Test
    void testBasedOnCopiesFields() {
        var original = MetadataDefinition.builder()
                                         .addString("a")
                                         .addInteger("b", "@B")
                                         .build();

        var copy = MetadataDefinition.builder(original).build();

        List<MetaField> origFields = collectFields(original);
        List<MetaField> copyFields = collectFields(copy);

        Assertions.assertEquals(origFields.size(), copyFields.size(), "Copy should have same number of fields");
        for (int i = 0; i < origFields.size(); i++) {
            var o = origFields.get(i);
            var c = copyFields.get(i);
            Assertions.assertEquals(o.fieldName(), c.fieldName());
            Assertions.assertEquals(o.formula(), c.formula());
            Assertions.assertEquals(o.fieldType(), c.fieldType());
        }

        // Mutate original builder and ensure copy does not change
        var newCopy = MetadataDefinition.builder(original)
                                        .addString("new")
                                        .build();

        Assertions.assertTrue(collectFields(original).stream()
                                                     .noneMatch(f -> f.fieldName().equals("new")),
                              "Copy should not be affected by further changes to original");
    }

    @Test
    void testInvalidArgumentThrows() {
        var builder = MetadataDefinition.builder();

        assertThrows(IllegalArgumentException.class,
                     () -> MetadataDefinition.builder(null));
        assertThrows(IllegalArgumentException.class,
                                () -> builder.addString(null));
        assertThrows(IllegalArgumentException.class,
                                () -> builder.addString("", "@F"));
        assertThrows(IllegalArgumentException.class,
                                () -> builder.addInteger("int", ""));
        assertThrows(IllegalArgumentException.class,
                                () -> builder.addLong("ln", null));
    }


}
