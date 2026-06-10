package com.ncba.countryinfo.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StringUtilsTest {

    @Test
    void returnsNullWhenInputIsNull() {
        assertThat(StringUtils.toSentenceCase(null)).isNull();
    }

    @Test
    void returnsBlankUnchanged() {
        assertThat(StringUtils.toSentenceCase("   ")).isEqualTo("   ");
        assertThat(StringUtils.toSentenceCase("")).isEqualTo("");
    }

    @ParameterizedTest
    @CsvSource({
            "kenya,Kenya",
            "KENYA,Kenya",
            "kEnYa,Kenya",
            "'UNITED STATES','United States'",
            "'united kingdom','United Kingdom'",
            "'  south   africa ','South Africa'",
            "t,T"
    })
    void convertsToSentenceCase(String input, String expected) {
        assertThat(StringUtils.toSentenceCase(input)).isEqualTo(expected);
    }
}
