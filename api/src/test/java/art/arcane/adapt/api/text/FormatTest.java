package art.arcane.adapt.api.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the output VolmLib's {@code Form} printed for each input; content text depends on it character for character. */
class FormatTest {
  @Test
  void numbersMatchTheOldHelper() {
    assertThat(Format.f(0.0D)).isEqualTo("0");
    assertThat(Format.f(0.0D, 0)).isEqualTo("0");
    assertThat(Format.f(0.0D, 1)).isEqualTo("0");
    assertThat(Format.f(0.0D, 2)).isEqualTo("0");
    assertThat(Format.f(0.0D, 3)).isEqualTo("0");
    assertThat(Format.f(0.04D)).isEqualTo("0");
    assertThat(Format.f(0.04D, 0)).isEqualTo("0");
    assertThat(Format.f(0.04D, 1)).isEqualTo("0");
    assertThat(Format.f(0.04D, 2)).isEqualTo("0.04");
    assertThat(Format.f(0.04D, 3)).isEqualTo("0.04");
    assertThat(Format.f(0.05D)).isEqualTo("0.1");
    assertThat(Format.f(0.05D, 0)).isEqualTo("0");
    assertThat(Format.f(0.05D, 1)).isEqualTo("0.1");
    assertThat(Format.f(0.05D, 2)).isEqualTo("0.05");
    assertThat(Format.f(0.05D, 3)).isEqualTo("0.05");
    assertThat(Format.f(0.15D)).isEqualTo("0.1");
    assertThat(Format.f(0.15D, 0)).isEqualTo("0");
    assertThat(Format.f(0.15D, 1)).isEqualTo("0.1");
    assertThat(Format.f(0.15D, 2)).isEqualTo("0.15");
    assertThat(Format.f(0.15D, 3)).isEqualTo("0.15");
    assertThat(Format.f(0.25D)).isEqualTo("0.2");
    assertThat(Format.f(0.25D, 0)).isEqualTo("0");
    assertThat(Format.f(0.25D, 1)).isEqualTo("0.2");
    assertThat(Format.f(0.25D, 2)).isEqualTo("0.25");
    assertThat(Format.f(0.25D, 3)).isEqualTo("0.25");
    assertThat(Format.f(1.0D)).isEqualTo("1");
    assertThat(Format.f(1.0D, 0)).isEqualTo("1");
    assertThat(Format.f(1.0D, 1)).isEqualTo("1");
    assertThat(Format.f(1.0D, 2)).isEqualTo("1");
    assertThat(Format.f(1.0D, 3)).isEqualTo("1");
    assertThat(Format.f(1.25D)).isEqualTo("1.2");
    assertThat(Format.f(1.25D, 0)).isEqualTo("1");
    assertThat(Format.f(1.25D, 1)).isEqualTo("1.2");
    assertThat(Format.f(1.25D, 2)).isEqualTo("1.25");
    assertThat(Format.f(1.25D, 3)).isEqualTo("1.25");
    assertThat(Format.f(1.5D)).isEqualTo("1.5");
    assertThat(Format.f(1.5D, 0)).isEqualTo("2");
    assertThat(Format.f(1.5D, 1)).isEqualTo("1.5");
    assertThat(Format.f(1.5D, 2)).isEqualTo("1.5");
    assertThat(Format.f(1.5D, 3)).isEqualTo("1.5");
    assertThat(Format.f(2.345D)).isEqualTo("2.3");
    assertThat(Format.f(2.345D, 0)).isEqualTo("2");
    assertThat(Format.f(2.345D, 1)).isEqualTo("2.3");
    assertThat(Format.f(2.345D, 2)).isEqualTo("2.35");
    assertThat(Format.f(2.345D, 3)).isEqualTo("2.345");
    assertThat(Format.f(12.5D)).isEqualTo("12.5");
    assertThat(Format.f(12.5D, 0)).isEqualTo("12");
    assertThat(Format.f(12.5D, 1)).isEqualTo("12.5");
    assertThat(Format.f(12.5D, 2)).isEqualTo("12.5");
    assertThat(Format.f(12.5D, 3)).isEqualTo("12.5");
    assertThat(Format.f(-3.75D)).isEqualTo("-3.8");
    assertThat(Format.f(-3.75D, 0)).isEqualTo("-4");
    assertThat(Format.f(-3.75D, 1)).isEqualTo("-3.8");
    assertThat(Format.f(-3.75D, 2)).isEqualTo("-3.75");
    assertThat(Format.f(-3.75D, 3)).isEqualTo("-3.75");
    assertThat(Format.f(999.95D)).isEqualTo("1000");
    assertThat(Format.f(999.95D, 0)).isEqualTo("1000");
    assertThat(Format.f(999.95D, 1)).isEqualTo("1000");
    assertThat(Format.f(999.95D, 2)).isEqualTo("999.95");
    assertThat(Format.f(999.95D, 3)).isEqualTo("999.95");
    assertThat(Format.f(12345.678D)).isEqualTo("12345.7");
    assertThat(Format.f(12345.678D, 0)).isEqualTo("12346");
    assertThat(Format.f(12345.678D, 1)).isEqualTo("12345.7");
    assertThat(Format.f(12345.678D, 2)).isEqualTo("12345.68");
    assertThat(Format.f(12345.678D, 3)).isEqualTo("12345.678");
    assertThat(Format.f(1234567.891D)).isEqualTo("1234567.9");
    assertThat(Format.f(1234567.891D, 0)).isEqualTo("1234568");
    assertThat(Format.f(1234567.891D, 1)).isEqualTo("1234567.9");
    assertThat(Format.f(1234567.891D, 2)).isEqualTo("1234567.89");
    assertThat(Format.f(1234567.891D, 3)).isEqualTo("1234567.891");
    assertThat(Format.f(0L)).isEqualTo("0");
    assertThat(Format.f(0L, 2)).isEqualTo("0");
    assertThat(Format.f(7L)).isEqualTo("7");
    assertThat(Format.f(7L, 2)).isEqualTo("7");
    assertThat(Format.f(-1234L)).isEqualTo("-1,234");
    assertThat(Format.f(-1234L, 2)).isEqualTo("-1234");
    assertThat(Format.f(12500L)).isEqualTo("12,500");
    assertThat(Format.f(12500L, 2)).isEqualTo("12500");
    assertThat(Format.f(1234567890123L)).isEqualTo("1,234,567,890,123");
  }

  @Test
  void percentagesMatchTheOldHelper() {
    assertThat(Format.pc(0.0D)).isEqualTo("0%");
    assertThat(Format.pc(0.0D, 0)).isEqualTo("0%");
    assertThat(Format.pc(0.0D, 1)).isEqualTo("0%");
    assertThat(Format.pc(0.0D, 2)).isEqualTo("0%");
    assertThat(Format.pc(0.005D)).isEqualTo("0%");
    assertThat(Format.pc(0.005D, 0)).isEqualTo("0%");
    assertThat(Format.pc(0.005D, 1)).isEqualTo("0.5%");
    assertThat(Format.pc(0.005D, 2)).isEqualTo("0.5%");
    assertThat(Format.pc(0.125D)).isEqualTo("12%");
    assertThat(Format.pc(0.125D, 0)).isEqualTo("12%");
    assertThat(Format.pc(0.125D, 1)).isEqualTo("12.5%");
    assertThat(Format.pc(0.125D, 2)).isEqualTo("12.5%");
    assertThat(Format.pc(0.5D)).isEqualTo("50%");
    assertThat(Format.pc(0.5D, 0)).isEqualTo("50%");
    assertThat(Format.pc(0.5D, 1)).isEqualTo("50%");
    assertThat(Format.pc(0.5D, 2)).isEqualTo("50%");
    assertThat(Format.pc(1.0D)).isEqualTo("100%");
    assertThat(Format.pc(1.0D, 0)).isEqualTo("100%");
    assertThat(Format.pc(1.0D, 1)).isEqualTo("100%");
    assertThat(Format.pc(1.0D, 2)).isEqualTo("100%");
    assertThat(Format.pc(1.255D)).isEqualTo("125%");
    assertThat(Format.pc(1.255D, 0)).isEqualTo("125%");
    assertThat(Format.pc(1.255D, 1)).isEqualTo("125.5%");
    assertThat(Format.pc(1.255D, 2)).isEqualTo("125.5%");
    assertThat(Format.pc(0.6666666666666666D)).isEqualTo("67%");
    assertThat(Format.pc(0.6666666666666666D, 0)).isEqualTo("67%");
    assertThat(Format.pc(0.6666666666666666D, 1)).isEqualTo("66.7%");
    assertThat(Format.pc(0.6666666666666666D, 2)).isEqualTo("66.67%");
    assertThat(Format.pc(-0.1D)).isEqualTo("-10%");
    assertThat(Format.pc(-0.1D, 0)).isEqualTo("-10%");
    assertThat(Format.pc(-0.1D, 1)).isEqualTo("-10%");
    assertThat(Format.pc(-0.1D, 2)).isEqualTo("-10%");
  }

  @Test
  void durationsMatchTheOldHelper() {
    assertThat(Format.duration(0.0D, 0)).isEqualTo("0ms");
    assertThat(Format.duration(0L, 0)).isEqualTo("0ms");
    assertThat(Format.duration(0.0D, 1)).isEqualTo("0ms");
    assertThat(Format.duration(0L, 1)).isEqualTo("0ms");
    assertThat(Format.duration(0.0D, 2)).isEqualTo("0ms");
    assertThat(Format.duration(0L, 2)).isEqualTo("0ms");
    assertThat(Format.duration(250.0D, 0)).isEqualTo("250ms");
    assertThat(Format.duration(250L, 0)).isEqualTo("250ms");
    assertThat(Format.duration(250.0D, 1)).isEqualTo("250ms");
    assertThat(Format.duration(250L, 1)).isEqualTo("250ms");
    assertThat(Format.duration(250.0D, 2)).isEqualTo("250ms");
    assertThat(Format.duration(250L, 2)).isEqualTo("250ms");
    assertThat(Format.duration(999.4D, 0)).isEqualTo("999ms");
    assertThat(Format.duration(999L, 0)).isEqualTo("999ms");
    assertThat(Format.duration(999.4D, 1)).isEqualTo("999.4ms");
    assertThat(Format.duration(999L, 1)).isEqualTo("999ms");
    assertThat(Format.duration(999.4D, 2)).isEqualTo("999.4ms");
    assertThat(Format.duration(999L, 2)).isEqualTo("999ms");
    assertThat(Format.duration(999.6D, 0)).isEqualTo("1000ms");
    assertThat(Format.duration(999.6D, 1)).isEqualTo("999.6ms");
    assertThat(Format.duration(999.6D, 2)).isEqualTo("999.6ms");
    assertThat(Format.duration(1000.0D, 0)).isEqualTo("1s");
    assertThat(Format.duration(1000L, 0)).isEqualTo("1 seconds");
    assertThat(Format.duration(1000.0D, 1)).isEqualTo("1s");
    assertThat(Format.duration(1000L, 1)).isEqualTo("1 seconds");
    assertThat(Format.duration(1000.0D, 2)).isEqualTo("1s");
    assertThat(Format.duration(1000L, 2)).isEqualTo("1 seconds");
    assertThat(Format.duration(1500.0D, 0)).isEqualTo("2s");
    assertThat(Format.duration(1500L, 0)).isEqualTo("2 seconds");
    assertThat(Format.duration(1500.0D, 1)).isEqualTo("1.5s");
    assertThat(Format.duration(1500L, 1)).isEqualTo("1.5 seconds");
    assertThat(Format.duration(1500.0D, 2)).isEqualTo("1.5s");
    assertThat(Format.duration(1500L, 2)).isEqualTo("1.5 seconds");
    assertThat(Format.duration(4250.0D, 0)).isEqualTo("4s");
    assertThat(Format.duration(4250L, 0)).isEqualTo("4 seconds");
    assertThat(Format.duration(4250.0D, 1)).isEqualTo("4.2s");
    assertThat(Format.duration(4250L, 1)).isEqualTo("4.2 seconds");
    assertThat(Format.duration(4250.0D, 2)).isEqualTo("4.25s");
    assertThat(Format.duration(4250L, 2)).isEqualTo("4.25 seconds");
    assertThat(Format.duration(59999.0D, 0)).isEqualTo("60s");
    assertThat(Format.duration(59999L, 0)).isEqualTo("60 seconds");
    assertThat(Format.duration(59999.0D, 1)).isEqualTo("60s");
    assertThat(Format.duration(59999L, 1)).isEqualTo("60 seconds");
    assertThat(Format.duration(59999.0D, 2)).isEqualTo("60s");
    assertThat(Format.duration(59999L, 2)).isEqualTo("60 seconds");
    assertThat(Format.duration(60000.0D, 0)).isEqualTo("1m");
    assertThat(Format.duration(60000L, 0)).isEqualTo("1 minutes");
    assertThat(Format.duration(60000.0D, 1)).isEqualTo("1m");
    assertThat(Format.duration(60000L, 1)).isEqualTo("1 minutes");
    assertThat(Format.duration(60000.0D, 2)).isEqualTo("1m");
    assertThat(Format.duration(60000L, 2)).isEqualTo("1 minutes");
    assertThat(Format.duration(90000.0D, 0)).isEqualTo("2m");
    assertThat(Format.duration(90000L, 0)).isEqualTo("2 minutes");
    assertThat(Format.duration(90000.0D, 1)).isEqualTo("1.5m");
    assertThat(Format.duration(90000L, 1)).isEqualTo("1.5 minutes");
    assertThat(Format.duration(90000.0D, 2)).isEqualTo("1.5m");
    assertThat(Format.duration(90000L, 2)).isEqualTo("1.5 minutes");
    assertThat(Format.duration(3599999.0D, 0)).isEqualTo("60m");
    assertThat(Format.duration(3599999L, 0)).isEqualTo("60 minutes");
    assertThat(Format.duration(3599999.0D, 1)).isEqualTo("60m");
    assertThat(Format.duration(3599999L, 1)).isEqualTo("60 minutes");
    assertThat(Format.duration(3599999.0D, 2)).isEqualTo("60m");
    assertThat(Format.duration(3599999L, 2)).isEqualTo("60 minutes");
    assertThat(Format.duration(3600000.0D, 0)).isEqualTo("1 hours");
    assertThat(Format.duration(3600000L, 0)).isEqualTo("1 hours");
    assertThat(Format.duration(3600000.0D, 1)).isEqualTo("1 hours");
    assertThat(Format.duration(3600000L, 1)).isEqualTo("1 hours");
    assertThat(Format.duration(3600000.0D, 2)).isEqualTo("1 hours");
    assertThat(Format.duration(3600000L, 2)).isEqualTo("1 hours");
    assertThat(Format.duration(5400000.0D, 0)).isEqualTo("2 hours");
    assertThat(Format.duration(5400000L, 0)).isEqualTo("2 hours");
    assertThat(Format.duration(5400000.0D, 1)).isEqualTo("1.5 hours");
    assertThat(Format.duration(5400000L, 1)).isEqualTo("1.5 hours");
    assertThat(Format.duration(5400000.0D, 2)).isEqualTo("1.5 hours");
    assertThat(Format.duration(5400000L, 2)).isEqualTo("1.5 hours");
    assertThat(Format.duration(8.6399999E7D, 0)).isEqualTo("24 hours");
    assertThat(Format.duration(86399999L, 0)).isEqualTo("24 hours");
    assertThat(Format.duration(8.6399999E7D, 1)).isEqualTo("24 hours");
    assertThat(Format.duration(86399999L, 1)).isEqualTo("24 hours");
    assertThat(Format.duration(8.6399999E7D, 2)).isEqualTo("24 hours");
    assertThat(Format.duration(86399999L, 2)).isEqualTo("24 hours");
    assertThat(Format.duration(8.64E7D, 0)).isEqualTo("60 days");
    assertThat(Format.duration(86400000L, 0)).isEqualTo("1 days");
    assertThat(Format.duration(8.64E7D, 1)).isEqualTo("60 days");
    assertThat(Format.duration(86400000L, 1)).isEqualTo("1 days");
    assertThat(Format.duration(8.64E7D, 2)).isEqualTo("60 days");
    assertThat(Format.duration(86400000L, 2)).isEqualTo("1 days");
    assertThat(Format.duration(1.296E8D, 0)).isEqualTo("90 days");
    assertThat(Format.duration(129600000L, 0)).isEqualTo("2 days");
    assertThat(Format.duration(1.296E8D, 1)).isEqualTo("90 days");
    assertThat(Format.duration(129600000L, 1)).isEqualTo("1.5 days");
    assertThat(Format.duration(1.296E8D, 2)).isEqualTo("90 days");
    assertThat(Format.duration(129600000L, 2)).isEqualTo("1.5 days");
    assertThat(Format.duration(6.04799999E8D, 0)).isEqualTo("420 days");
    assertThat(Format.duration(604799999L, 0)).isEqualTo("7 days");
    assertThat(Format.duration(6.04799999E8D, 1)).isEqualTo("420 days");
    assertThat(Format.duration(604799999L, 1)).isEqualTo("7 days");
    assertThat(Format.duration(6.04799999E8D, 2)).isEqualTo("420 days");
    assertThat(Format.duration(604799999L, 2)).isEqualTo("7 days");
    assertThat(Format.duration(6.048E8D, 0)).isEqualTo("604800000ms");
    assertThat(Format.duration(604800000L, 0)).isEqualTo("604800000ms");
    assertThat(Format.duration(6.048E8D, 1)).isEqualTo("604800000ms");
    assertThat(Format.duration(604800000L, 1)).isEqualTo("604800000ms");
    assertThat(Format.duration(6.048E8D, 2)).isEqualTo("604800000ms");
    assertThat(Format.duration(604800000L, 2)).isEqualTo("604800000ms");
    assertThat(Format.duration(1.0E10D, 0)).isEqualTo("10000000000ms");
    assertThat(Format.duration(10000000000L, 0)).isEqualTo("10000000000ms");
    assertThat(Format.duration(1.0E10D, 1)).isEqualTo("10000000000ms");
    assertThat(Format.duration(10000000000L, 1)).isEqualTo("10000000000ms");
    assertThat(Format.duration(1.0E10D, 2)).isEqualTo("10000000000ms");
    assertThat(Format.duration(10000000000L, 2)).isEqualTo("10000000000ms");
    assertThat(Format.duration(0L)).isEqualTo("0ms");
    assertThat(Format.duration(250L)).isEqualTo("250ms");
    assertThat(Format.duration(999L)).isEqualTo("999ms");
    assertThat(Format.duration(1000L)).isEqualTo("1 seconds");
    assertThat(Format.duration(1500L)).isEqualTo("2 seconds");
    assertThat(Format.duration(4250L)).isEqualTo("4 seconds");
    assertThat(Format.duration(59999L)).isEqualTo("60 seconds");
    assertThat(Format.duration(60000L)).isEqualTo("1 minutes");
    assertThat(Format.duration(90000L)).isEqualTo("2 minutes");
    assertThat(Format.duration(3599999L)).isEqualTo("60 minutes");
    assertThat(Format.duration(3600000L)).isEqualTo("1 hours");
    assertThat(Format.duration(5400000L)).isEqualTo("2 hours");
    assertThat(Format.duration(86399999L)).isEqualTo("24 hours");
    assertThat(Format.duration(86400000L)).isEqualTo("1 days");
    assertThat(Format.duration(129600000L)).isEqualTo("2 days");
    assertThat(Format.duration(604799999L)).isEqualTo("7 days");
    assertThat(Format.duration(604800000L)).isEqualTo("604800000ms");
    assertThat(Format.duration(10000000000L)).isEqualTo("10000000000ms");
  }

  @Test
  void capitalizedWordsMatchTheOldHelper() {
    assertThat(Format.capitalizeWords("")).isEqualTo("");
    assertThat(Format.capitalizeWords("a")).isEqualTo("A");
    assertThat(Format.capitalizeWords("hello world")).isEqualTo("Hello World");
    assertThat(Format.capitalizeWords("  padded  words ")).isEqualTo("Padded  Words");
    assertThat(Format.capitalizeWords("double  space")).isEqualTo("Double  Space");
    assertThat(Format.capitalizeWords("mIxed CASE")).isEqualTo("MIxed CASE");
    assertThat(Format.capitalizeWords("tab\tinside")).isEqualTo("Tab\tinside");
    assertThat(Format.capitalizeWords("x")).isEqualTo("X");
  }

  @Test
  void romanNumeralsMatchTheOldHelper() {
    assertThat(Format.toRoman(-3)).isEqualTo("");
    assertThat(Format.toRoman(0)).isEqualTo("");
    assertThat(Format.toRoman(1)).isEqualTo("I");
    assertThat(Format.toRoman(4)).isEqualTo("IV");
    assertThat(Format.toRoman(9)).isEqualTo("IX");
    assertThat(Format.toRoman(14)).isEqualTo("XIV");
    assertThat(Format.toRoman(40)).isEqualTo("XL");
    assertThat(Format.toRoman(90)).isEqualTo("XC");
    assertThat(Format.toRoman(400)).isEqualTo("CD");
    assertThat(Format.toRoman(1994)).isEqualTo("MCMXCIV");
    assertThat(Format.toRoman(3999)).isEqualTo("MMMCMXCIX");
  }

  @Test
  void largeWholeNumbersPrintExactly() {
    assertThat(Format.f(1234567890123L, 2)).isEqualTo("1234567890123");
    assertThat(Format.duration(16777217000L, 0)).isEqualTo("16777217000ms");
  }
}
