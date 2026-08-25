package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationIssue;
import art.arcane.volmlib.util.localization.LocalizationManager;
import art.arcane.volmlib.util.localization.LocalizationReloadResult;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.LocalizationValidationResult;
import art.arcane.volmlib.util.localization.LocalizationValidator;
import art.arcane.volmlib.util.localization.LinesValue;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.PluralValue;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.VolmitLocales;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;
import static org.assertj.core.api.Assertions.assertThat;

class AdaptLanguageTest extends AdaptTestBase {
  private static final int MAX_INTEGRITY_FAILURES = 100;
  private static final Path RESOURCE_ROOT = Path.of("src/main/resources");
  private static final Path SOURCE_ROOT = Path.of("src/main/java");
  private static final Pattern PROTOCOL_TOKEN = Pattern.compile(
      "/adapt(?:\\s+(?:clear\\s+adaptations|determine))?"
          + "|https?://[^\\s]+"
          + "|(?i:&x(?:&[0-9a-f]){6}|&#[0-9a-f]{6}|§x(?:§[0-9a-f]){6}|[§&][0-9a-fk-or])"
          + "|\\{[^{}\\n]+}"
          + "|<[^<>\\n]+>"
          + "|\\b[a-z][a-z0-9_]*:[a-z0-9_./{}-]+\\b"
          + "|\\badapt\\.[a-z0-9_.]*[a-z0-9_]\\b"
          + "|\\b[A-Za-z][A-Za-z0-9_-]*=\\{[^{}\\n]+}"
          + "|\\bconfig-archive\\b"
          + "|\\b(?:enabled=(?:on|off|clear|true|false)|force=true|seed=123)\\b"
          + "|%"
  );
  private static final Pattern ESCAPE_TOKEN = Pattern.compile("\\\\.");
  private static final Pattern PROTECTED_TERM = Pattern.compile(
      "(?<![A-Za-z0-9])(?:PlaceholderAPI|GriefDefender|CraftBukkit|WorldGuard|NeoForge|Minecraft"
          + "|TragOul|Chronos|Adapt|Bukkit|Paper|Folia|Purpur|Fabric|Iris|PAPI|NMS|NBT|JSON"
          + "|TOML|YAML)(?![A-Za-z0-9])"
  );
  private static final Pattern WORD_TOKEN = Pattern.compile("[\\p{L}\\p{N}_]+");
  private static final Pattern FRENCH_FALSE_OCCURRENCE_TERM = Pattern.compile("(?iu)\\bheures?\\b");
  private static final Pattern SPANISH_FALSE_PROJECTILE_TERM = Pattern.compile("(?iu)\\bproyectores?\\b");
  private static final Pattern SPANISH_FALSE_TICK_TERM = Pattern.compile("(?iu)\\bgarrapatas?\\b");
  private static final Pattern CHINESE_FALSE_CAST_TERM = Pattern.compile("铸造|鑄造");
  private static final Pattern CHINESE_FALSE_COMPANY_TERM = Pattern.compile("公司");
  private static final Pattern CHINESE_FALSE_GUARD_TERM = Pattern.compile("警卫|警衛");
  private static final Pattern CHINESE_FALSE_OCCURRENCE_TERM = Pattern.compile("时间|時間");
  private static final Pattern CHINESE_FALSE_SLIDE_TERM = Pattern.compile("幻灯|幻燈");
  private static final Pattern CHINESE_FALSE_SPRINT_JUMP_TERM = Pattern.compile("跳伞|跳傘");
  private static final Map<String, Pattern> FALSE_OCCURRENCE_TERMS = Map.ofEntries(
      Map.entry("de_DE", Pattern.compile("(?iu)\\bstunden?\\b")),
      Map.entry("fi_FI", Pattern.compile("(?iu)\\btunti(?:a|en)?\\b")),
      Map.entry("he_IL", Pattern.compile("שעות")),
      Map.entry("it_IT", Pattern.compile("(?iu)\\bore\\b")),
      Map.entry("nl_NL", Pattern.compile("(?iu)\\buren?\\b")),
      Map.entry("pl_PL", Pattern.compile("(?iu)\\bgodzin\\w*\\b")),
      Map.entry("pt_PT", Pattern.compile("(?iu)\\bhoras?\\b")),
      Map.entry("ru_RU", Pattern.compile("(?iu)\\bчас(?:а|ов)?\\b")),
      Map.entry("tr_TR", Pattern.compile("(?iu)\\bsaat\\w*\\b"))
  );
  private static final Map<String, Pattern> FALSE_PROJECTILE_TERMS = Map.ofEntries(
      Map.entry("de_DE", Pattern.compile("(?iu)\\bprojektor(?:en|s)?\\b")),
      Map.entry("fi_FI", Pattern.compile("(?iu)\\bprojektori\\w*\\b")),
      Map.entry("he_IL", Pattern.compile("מקרן")),
      Map.entry("it_IT", Pattern.compile("(?iu)\\bproiettore\\w*\\b")),
      Map.entry("ja-JP", Pattern.compile("プロジェクタ(?:ー)?")),
      Map.entry("nl_NL", Pattern.compile("(?iu)\\bprojectoren?\\b")),
      Map.entry("pl_PL", Pattern.compile("(?iu)\\bprojektor\\w*\\b")),
      Map.entry("pt_PT", Pattern.compile("(?iu)\\bprojetor(?:es)?\\b")),
      Map.entry("ru_RU", Pattern.compile("(?iu)\\bпроектор\\w*\\b")),
      Map.entry("tr_TR", Pattern.compile("(?iu)\\bprojekt[öo]r\\w*\\b"))
  );
  private static final Map<String, Pattern> FALSE_TICK_TERMS = Map.ofEntries(
      Map.entry("de_DE", Pattern.compile("(?iu)\\bzecken?\\b")),
      Map.entry("fi_FI", Pattern.compile("(?iu)\\bpunkki\\w*\\b")),
      Map.entry("he_IL", Pattern.compile("קרצי")),
      Map.entry("it_IT", Pattern.compile("(?iu)\\bzecche?\\b")),
      Map.entry("ja-JP", Pattern.compile("ダニ")),
      Map.entry("nl_NL", Pattern.compile("(?iu)\\bteken?\\b")),
      Map.entry("pl_PL", Pattern.compile("(?iu)\\bkleszcz\\w*\\b")),
      Map.entry("pt_PT", Pattern.compile("(?iu)\\bcarrapat\\w*\\b")),
      Map.entry("ru_RU", Pattern.compile("(?iu)\\bклещ\\w*\\b")),
      Map.entry("tr_TR", Pattern.compile("(?iu)\\bkene\\w*\\b"))
  );
  private static final Map<String, Pattern> FALSE_SLIDE_TERMS = Map.ofEntries(
      Map.entry("de_DE", Pattern.compile("(?iu)\\b(?:diashow|folien?)\\b")),
      Map.entry("fi_FI", Pattern.compile("(?iu)\\bdia(?:esitys|kuva)\\w*\\b")),
      Map.entry("he_IL", Pattern.compile("שקופ")),
      Map.entry("it_IT", Pattern.compile("(?iu)\\bdiapositiv\\w*\\b")),
      Map.entry("nl_NL", Pattern.compile("(?iu)\\b(?:dia|dia's|diavoorstellingen?)\\b")),
      Map.entry("pl_PL", Pattern.compile("(?iu)\\bslajd\\w*\\b")),
      Map.entry("pt_PT", Pattern.compile("(?iu)\\b(?:diapositiv|apresenta[cç][aã]o)\\w*\\b")),
      Map.entry("ru_RU", Pattern.compile("(?iu)\\bслайд\\w*\\b")),
      Map.entry("tr_TR", Pattern.compile("(?iu)\\bslayt\\w*\\b"))
  );
  private static final Pattern TRIPLE_WORD_REPEAT = Pattern.compile(
      "(?iu)\\b(\\p{L}{3,})\\b(?:[\\s,:;.!?-]+\\1\\b){2,}"
  );
  private static final Pattern FORBIDDEN_TRANSLATION_ARTIFACT = Pattern.compile(
      "(?iu)(?:\\bcannot\\b|\\bget(?:[\\s,:;.!?-]+get)+\\b|お問い合わせ|单位:千美元|單位:千美元"
          + "|(?:^|\\s)B\\.\\s+\\{|\\bmafia\\w*\\b|\\bmaffia\\w*\\b|\\bmafija\\w*\\b"
          + "|\\bmáfia\\w*\\b|мафи\\w*|黑手党|黑手黨|マフィア|마피아|המאפיה"
          + "|YOU MAY NOT UNLEARN|REFUNDS DISABLED|Canton|圣经化|聖經化|слева-слева"
          + "|Hayır,\\s*hayır|@ info:\\s*(?:tooltip|whatsthis)|GenericName"
          + "|City name optional, probably does not need a translation|unit description in lists"
          + "|weather condition|weather forecast|City in California USA"
          + "|great-\\s*britain\\s*_\\s*counties\\.\\s*kgm|zambia\\s*_\\s*districts\\.\\s*kgm"
          + "|Wiser\\s+Skull|Wither\\s+SColl|/Potem|stneak"
          + "|Right-\\s*Klik|Left-\\s*Klik|Doğru\\s+tıklama|제품\\s*정보|문의\\s*사항"
          + "|연락처|이름\\s*\\*|여행\\s*일정|기타\\s*제품|뚱\\s*베어)"
  );
  private static final Pattern CJK_UNEXPECTED_SCRIPT_ARTIFACT = Pattern.compile(
      "(?u)(?:ưμ㼯A|临Τ|臨Τ|[\\p{IsGreek}\\p{IsCyrillic}])"
  );
  private static final Pattern GENERIC_LABEL_ARTIFACT = Pattern.compile("(?i)\\b(?:Name|Comment)\\b");
  private static final Pattern LITHUANIAN_TRANSLATION_ARTIFACT = Pattern.compile(
      "(?iu)(?:\\b(?:Name|Comment)\\b|[\\p{IsCyrillic}])"
  );
  private static final Map<String, Pattern> LOCALE_TRANSLATION_ARTIFACTS = Map.ofEntries(
      Map.entry("fi_FI", Pattern.compile("(?i)City name optional")),
      Map.entry("ja-JP", Pattern.compile(
          "(?:ログイン|サイトマップ|新着情報|最近の投稿|ニュース|プロフィール|電話番号|ご利用案内"
              + "|お問い合わせ|会社情報|製品情報|商品情報|プライバシー|ホームページ|ブログ|カテゴリ|アーカイブ)"
      )),
      Map.entry("ko_KR", Pattern.compile(
          "(?iu)(?:제품\\s*정보|문의\\s*사항|연락처|이름\\s*\\*|여행\\s*일정|기타\\s*제품|뚱\\s*베어"
              + "|관련\\s*(?:제품|상품|기사)|지원하다|주요\\s*(?:연혁|특징|사업)|오시는\\s*길|사이트맵|학회소개"
              + "|회사연혁|내\\s*계정|인기\\s*카테고리|스낵\\s*바|계정\\s*관리|지도보기"
              + "|자주\\s*묻는\\s*질문|옵션\\s*정보|주\\s*메뉴|계정\\s*만들기|증권\\s*시세\\s*표시|환경\\s*정책)"
      )),
      Map.entry("lt_LT", LITHUANIAN_TRANSLATION_ARTIFACT),
      Map.entry("vi_VI", GENERIC_LABEL_ARTIFACT),
      Map.entry("zh_CN", CJK_UNEXPECTED_SCRIPT_ARTIFACT),
      Map.entry("zh_TW", CJK_UNEXPECTED_SCRIPT_ARTIFACT)
  );
  private static final Pattern CJK_FORBIDDEN_SCRIPT = Pattern.compile(
      "[\\p{IsGreek}\\p{IsCyrillic}\\p{IsHebrew}\\p{IsArabic}\\p{IsHangul}\\p{IsHiragana}\\p{IsKatakana}]"
  );
  private static final Pattern JAPANESE_FORBIDDEN_SCRIPT = Pattern.compile(
      "[\\p{IsGreek}\\p{IsCyrillic}\\p{IsHebrew}\\p{IsArabic}\\p{IsHangul}]"
  );
  private static final Pattern KOREAN_FORBIDDEN_SCRIPT = Pattern.compile(
      "[\\p{IsGreek}\\p{IsCyrillic}\\p{IsHebrew}\\p{IsArabic}\\p{IsHiragana}\\p{IsKatakana}]"
  );
  private static final Pattern LATIN_FORBIDDEN_SCRIPT = Pattern.compile(
      "[\\p{IsGreek}\\p{IsCyrillic}\\p{IsHebrew}\\p{IsArabic}\\p{IsHan}\\p{IsHangul}\\p{IsHiragana}\\p{IsKatakana}]"
  );
  private static final Pattern HEBREW_FORBIDDEN_SCRIPT = Pattern.compile(
      "[\\p{IsGreek}\\p{IsCyrillic}\\p{IsArabic}\\p{IsHan}\\p{IsHangul}\\p{IsHiragana}\\p{IsKatakana}]"
  );
  private static final Pattern CYRILLIC_FORBIDDEN_SCRIPT = Pattern.compile(
      "[\\p{IsGreek}\\p{IsHebrew}\\p{IsArabic}\\p{IsHan}\\p{IsHangul}\\p{IsHiragana}\\p{IsKatakana}]"
  );
  private static final Map<String, Pattern> UNEXPECTED_SCRIPT_ARTIFACTS = Map.ofEntries(
      Map.entry("de_DE", LATIN_FORBIDDEN_SCRIPT),
      Map.entry("es_ES", LATIN_FORBIDDEN_SCRIPT),
      Map.entry("fi_FI", LATIN_FORBIDDEN_SCRIPT),
      Map.entry("fr_FR", LATIN_FORBIDDEN_SCRIPT),
      Map.entry("he_IL", HEBREW_FORBIDDEN_SCRIPT),
      Map.entry("it_IT", LATIN_FORBIDDEN_SCRIPT),
      Map.entry("ja-JP", JAPANESE_FORBIDDEN_SCRIPT),
      Map.entry("ko_KR", KOREAN_FORBIDDEN_SCRIPT),
      Map.entry("lt_LT", LATIN_FORBIDDEN_SCRIPT),
      Map.entry("nl_NL", LATIN_FORBIDDEN_SCRIPT),
      Map.entry("pl_PL", LATIN_FORBIDDEN_SCRIPT),
      Map.entry("pt_PT", LATIN_FORBIDDEN_SCRIPT),
      Map.entry("ru_RU", CYRILLIC_FORBIDDEN_SCRIPT),
      Map.entry("tr_TR", LATIN_FORBIDDEN_SCRIPT),
      Map.entry("vi_VI", LATIN_FORBIDDEN_SCRIPT),
      Map.entry("zh_CN", CJK_FORBIDDEN_SCRIPT),
      Map.entry("zh_TW", CJK_FORBIDDEN_SCRIPT)
  );
  private static final Map<String, Pattern> FORBIDDEN_COOLDOWN_TERMS = Map.ofEntries(
      Map.entry("de_DE", Pattern.compile("(?iu)cooldowns?|kühl|kälte")),
      Map.entry("es_ES", Pattern.compile("(?iu)cooldowns?|refrig|enfri")),
      Map.entry("fi_FI", Pattern.compile("(?iu)cooldowns?|jäähd|jääht")),
      Map.entry("fr_FR", Pattern.compile("(?iu)cooldowns?|refroid")),
      Map.entry("he_IL", Pattern.compile("(?iu)cooldowns?|הצטמננות|ה?קירור|קיל(?:אודון|אוד|און|דאון)")),
      Map.entry("it_IT", Pattern.compile("(?iu)cooldowns?|raffredd|rinfresc")),
      Map.entry("ja-JP", Pattern.compile("(?iu)cooldowns?|冷却")),
      Map.entry("ko_KR", Pattern.compile("(?iu)cooldowns?|연락처|냉각|쿨타임|쿨다운|콜다운")),
      Map.entry("lt_LT", Pattern.compile("(?iu)cooldowns?|balandis|aušin|atvės|atves")),
      Map.entry("nl_NL", Pattern.compile("(?iu)cooldowns?|koeling|koeldown")),
      Map.entry("pl_PL", Pattern.compile("(?iu)cooldowns?|chłodz|schładz|ochładz")),
      Map.entry("pt_PT", Pattern.compile("(?iu)cooldowns?|refrig|arref|resfri|esfri|frigorífico")),
      Map.entry("ru_RU", Pattern.compile("(?iu)cooldowns?|охлажд|кулдаун|осморож")),
      Map.entry("tr_TR", Pattern.compile("(?iu)cooldowns?|soğut|sakinleş")),
      Map.entry("vi_VI", Pattern.compile("(?iu)cooldowns?|làm mát|nguội lạnh|hạ xuống|hạ nhiệt")),
      Map.entry("zh_CN", Pattern.compile("(?iu)cooldowns?|冷却(?!时间)|降温|倒计时|平凉")),
      Map.entry("zh_TW", Pattern.compile("(?iu)cooldowns?|冷卻(?!時間)|降溫|倒計時|平涼"))
  );
  private static final Map<String, Pattern> CJK_ASCII_ARTIFACTS = Map.ofEntries(
      Map.entry("ja-JP", Pattern.compile(
          "(?i)\\b(?:Mutation|Sneak|Proc|Momentum|Nether|Void|Shapeless|potion|servants|melee"
              + "|Block|sec|Axe|Stagger|haste|Projectile|Enemies|Config)\\b"
      )),
      Map.entry("ko_KR", Pattern.compile(
          "(?i)\\b(?:Mutation|Sneak|mobs?|Block|Knockback|Haste|sec|Shred|Proc|Trigger"
              + "|Projectiles?|config|Decoys?|Buff|axe|Weakness|Riposte|Ricochet|combo|orb"
              + "|enchant|stasis|plague|villager|Netherite)\\b"
      )),
      Map.entry("zh_CN", Pattern.compile(
          "(?i)\\b(?:Block|sec|Bash|Shapeless|Proc|Burrow|Bloom|Temperbound|Deepblood|Tick"
              + "|Skeleton|Ricochet|Weder|works)\\b"
      )),
      Map.entry("zh_TW", Pattern.compile(
          "(?i)\\b(?:Block|sec|Bash|Shapeless|Proc|Burrow|Bloom|Temperbound|Deepblood|Tick"
              + "|Skeleton|Ricochet|Weder|works)\\b"
      ))
  );

  @Test
  void codeOwnedCatalogContainsUsableEnglishForEveryKey() {
    MessageCatalog catalog = AdaptMessages.catalog();

    assertThat(catalog.englishLocale()).isEqualTo("en_US");
    assertThat(catalog.keys()).isNotEmpty();
    for (MessageKey key : catalog.keys()) {
      assertThat(key.englishValue().placeholders()).isNotNull();
      assertThat(key.englishValue().toString()).isNotBlank();
    }
  }

  @Test
  void everyDownloadSourceLocaleParsesAndMatchesCatalogShapes() throws Exception {
    for (String locale : VolmitLocales.nonEnglish()) {
      Path localeFile = RESOURCE_ROOT.resolve(locale + ".toml");
      assertThat(localeFile).exists();
      LocaleOverlay overlay = AdaptLanguage.parseOverlay(
          localeFile.toString(),
          locale,
          Files.readString(localeFile)
      );
      LocalizationValidationResult validation = LocalizationValidator.validate(
          AdaptMessages.catalog(),
          List.of(overlay)
      );
      assertThat(validation.errors())
          .describedAs("locale errors in %s", localeFile)
          .isEmpty();
    }
  }

  @Test
  void downloadSourcesAreExactlyTheNonEnglishLocales() throws Exception {
    List<String> expected = VolmitLocales.nonEnglish().stream()
        .map(locale -> locale + ".toml")
        .sorted()
        .toList();
    List<String> actual;
    try (Stream<Path> resources = Files.list(RESOURCE_ROOT)) {
      actual = resources
          .filter(path -> path.getFileName().toString().endsWith(".toml"))
          .map(path -> path.getFileName().toString())
          .sorted()
          .toList();
    }

    assertThat(actual).containsExactlyElementsOf(expected);
  }

  @Test
  void everyDownloadSourceLocaleCoversTheEntireCatalog() throws Exception {
    MessageCatalog catalog = AdaptMessages.catalog();
    for (String locale : VolmitLocales.nonEnglish()) {
      Path localeFile = RESOURCE_ROOT.resolve(locale + ".toml");
      LocaleOverlay overlay = AdaptLanguage.parseOverlay(
          localeFile.toString(),
          locale,
          Files.readString(localeFile)
      );

      assertThat(overlay.values().keySet())
          .describedAs("catalog coverage in %s", localeFile)
          .containsExactlyInAnyOrderElementsOf(catalog.byId().keySet());
    }
  }

  @Test
  void everyDownloadSourceTranslationIsNonBlankAndPreservesProtocolTokens() throws Exception {
    MessageCatalog catalog = AdaptMessages.catalog();
    List<String> failures = new ArrayList<>();
    int failureCount = 0;
    for (String locale : VolmitLocales.nonEnglish()) {
      Path localeFile = RESOURCE_ROOT.resolve(locale + ".toml");
      LocaleOverlay overlay = AdaptLanguage.parseOverlay(
          localeFile.toString(),
          locale,
          Files.readString(localeFile)
      );
      for (MessageKey key : catalog.keys()) {
        try {
          assertValueIntegrity(locale, key.id(), key.englishValue(), overlay.value(key.id()));
        } catch (AssertionError error) {
          failureCount++;
          if (failures.size() < MAX_INTEGRITY_FAILURES) {
            failures.add(firstLine(error));
          }
        }
      }
    }
    if (failureCount > failures.size()) {
      failures.add((failureCount - failures.size()) + " additional integrity failures were omitted");
    }

    assertThat(failures).isEmpty();
  }

  @Test
  void pluralTablesRemainPluralOverlayValues() {
    LocaleOverlay overlay = AdaptLanguage.parseOverlay(
        "test",
        "test",
        "[items.multi_armor.count]\none = \"Ein Gegenstand ({count})\"\nother = \"{count} Gegenstände\"\n"
    );

    assertThat(overlay.value("items.multi_armor.count")).isInstanceOf(PluralValue.class);
    PluralValue value = (PluralValue) overlay.value("items.multi_armor.count");
    assertThat(value.forms()).containsOnlyKeys("one", "other");
    assertThat(LocalizationValidator.validate(AdaptMessages.catalog(), List.of(overlay)).errors()).isEmpty();
  }

  @Test
  void trustedFormattingRendersWhileUntrustedFormattingCannotInjectColors() {
    MessageArgs arguments = MessageArgs.builder()
        .add(trusted("trusted", "&aTrusted"))
        .add(untrusted("untrusted", "&cInjected§d<click:run_command:'/op'>[12ABef]"))
        .build();

    String rendered = AdaptLanguage.renderTemplate(
        "safe={trusted}; unsafe={untrusted}",
        arguments,
        false
    );

    assertThat(rendered).isEqualTo("safe=§aTrusted; unsafe=＆cInjected＜click:run_command:'/op'＞［12ABef］");
  }

  @Test
  void insertedArgumentsAreNeverReprocessedAsLaterSentinels() {
    MessageArgs arguments = MessageArgs.builder()
        .add(untrusted("first", "\uE0001\uE001"))
        .add(untrusted("second", "replacement"))
        .build();

    String rendered = AdaptLanguage.renderTemplate("{first}|{second}", arguments, false);

    assertThat(rendered).isEqualTo("\uE0001\uE001|replacement");
  }

  @Test
  void rejectedAdaptOverlayRetainsTheLastGoodSnapshot() {
    MessageCatalog catalog = AdaptMessages.catalog();
    LocalizationManager manager = new LocalizationManager(LocalizationCandidate.english(
        catalog,
        PluralSelector.oneOther()
    ));
    LocaleOverlay valid = LocaleOverlay.builder("valid", "test")
        .text("runtime.no_description_provided", "Translated description")
        .build();
    LocalizationReloadResult applied = manager.reload(new LocalizationCandidate(
        catalog,
        List.of(valid),
        PluralSelector.oneOther()
    ));
    LocalizationSnapshot lastGood = manager.snapshot();
    LocaleOverlay invalid = LocaleOverlay.builder("invalid", "test")
        .text("runtime.no_description_provided", "Would replace the last good value")
        .text("unknown.key", "Invalid")
        .build();
    LocalizationReloadResult rejected = manager.reload(new LocalizationCandidate(
        catalog,
        List.of(invalid),
        PluralSelector.oneOther()
    ));

    assertThat(applied.applied()).isTrue();
    assertThat(rejected.applied()).isFalse();
    assertThat(rejected.current()).isSameAs(lastGood);
    assertThat(manager.snapshot()).isSameAs(lastGood);
    assertThat(manager.snapshot().resolve((TextKey) catalog.require("runtime.no_description_provided")).template())
        .isEqualTo("Translated description");
    assertThat(rejected.validation().errors())
        .extracting(LocalizationIssue::key)
        .contains("unknown.key");
  }

  @Test
  void sourceContainsNoLegacyStringLookupPath() throws Exception {
    StringBuilder source = new StringBuilder();
    try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
      List<Path> javaFiles = files.filter(path -> path.toString().endsWith(".java")).toList();
      for (Path javaFile : javaFiles) {
        source.append(Files.readString(javaFile));
      }
    }

    assertThat(source).doesNotContain("class Localizer");
    assertThat(source).doesNotContain(".wordKey(");
    assertThat(source).doesNotContain("AdaptLanguage.text(\"");
  }

  private static void assertValueIntegrity(
      String locale,
      String key,
      MessageValue english,
      MessageValue translated
  ) {
    if (english instanceof TextValue englishText && translated instanceof TextValue translatedText) {
      assertTemplateIntegrity(locale, key, englishText.template(), translatedText.template());
      return;
    }
    if (english instanceof LinesValue englishLines && translated instanceof LinesValue translatedLines) {
      assertThat(translatedLines.lines()).describedAs("line shape for %s:%s", locale, key)
          .hasSameSizeAs(englishLines.lines());
      for (int index = 0; index < englishLines.lines().size(); index++) {
        assertTemplateIntegrity(
            locale,
            key + "[" + index + "]",
            englishLines.lines().get(index),
            translatedLines.lines().get(index)
        );
      }
      return;
    }
    if (english instanceof PluralValue englishPlural && translated instanceof PluralValue translatedPlural) {
      assertThat(translatedPlural.forms().keySet()).describedAs("plural shape for %s:%s", locale, key)
          .containsExactlyInAnyOrderElementsOf(englishPlural.forms().keySet());
      for (Map.Entry<String, String> form : englishPlural.forms().entrySet()) {
        assertTemplateIntegrity(
            locale,
            key + "." + form.getKey(),
            form.getValue(),
            translatedPlural.forms().get(form.getKey())
        );
      }
      return;
    }
    throw new AssertionError("Message shape mismatch for " + locale + ":" + key);
  }

  private static void assertTemplateIntegrity(String locale, String key, String english, String translated) {
    assertThat(translated)
        .describedAs("translation text for %s:%s", locale, key)
        .isNotBlank()
        .doesNotContain("\uFFFD", "\uE000", "\uE001");
    assertThat(protocolTokens(translated))
        .describedAs("protocol tokens for %s:%s", locale, key)
        .containsExactlyElementsOf(protocolTokens(english));
    assertThat(patternTokens(ESCAPE_TOKEN, translated))
        .describedAs("escape tokens for %s:%s", locale, key)
        .containsExactlyElementsOf(patternTokens(ESCAPE_TOKEN, english));
    if (delimiterIssue(english) == null) {
      assertThat(delimiterIssue(translated))
          .describedAs("balanced delimiters for %s:%s", locale, key)
          .isNull();
    }
    assertThat(patternTokens(PROTECTED_TERM, translated))
        .describedAs("protected terms for %s:%s", locale, key)
        .containsExactlyElementsOf(patternTokens(PROTECTED_TERM, english));
    assertThat(translated.length())
        .describedAs("translation length for %s:%s", locale, key)
        .isLessThanOrEqualTo(Math.max(300, english.length() * 4 + 80));
    assertThat(FORBIDDEN_TRANSLATION_ARTIFACT.matcher(translated).find())
        .describedAs("known translation artifact for %s:%s", locale, key)
        .isFalse();
    String inspectable = PROTOCOL_TOKEN.matcher(translated).replaceAll("");
    Pattern localeArtifact = LOCALE_TRANSLATION_ARTIFACTS.get(locale);
    if (localeArtifact != null) {
      assertThat(localeArtifact.matcher(inspectable).find())
          .describedAs("locale-specific translation artifact for %s:%s", locale, key)
          .isFalse();
    }
    Pattern unexpectedScript = UNEXPECTED_SCRIPT_ARTIFACTS.get(locale);
    if (unexpectedScript != null) {
      assertThat(unexpectedScript.matcher(inspectable).find())
          .describedAs("unexpected translation script for %s:%s", locale, key)
          .isFalse();
    }
    String normalizedEnglish = english.toLowerCase(Locale.ROOT);
    if (locale.equals("fr_FR") && normalizedEnglish.contains(" times")) {
      assertThat(FRENCH_FALSE_OCCURRENCE_TERM.matcher(inspectable).find())
          .describedAs("French occurrence terminology for %s", key)
          .isFalse();
    }
    if (locale.equals("es_ES") && normalizedEnglish.contains("projectile")) {
      assertThat(SPANISH_FALSE_PROJECTILE_TERM.matcher(inspectable).find())
          .describedAs("Spanish projectile terminology for %s", key)
          .isFalse();
    }
    if (locale.equals("es_ES") && normalizedEnglish.contains("tick")) {
      assertThat(SPANISH_FALSE_TICK_TERM.matcher(inspectable).find())
          .describedAs("Spanish tick terminology for %s", key)
          .isFalse();
    }
    Pattern occurrenceTerm = FALSE_OCCURRENCE_TERMS.get(locale);
    if (occurrenceTerm != null
        && normalizedEnglish.contains(" times")
        && !normalizedEnglish.contains("hourglass")
        && !normalizedEnglish.contains("clock")
        && !normalizedEnglish.contains("watch")) {
      assertThat(occurrenceTerm.matcher(inspectable).find())
          .describedAs("occurrence terminology for %s:%s", locale, key)
          .isFalse();
    }
    Pattern projectileTerm = FALSE_PROJECTILE_TERMS.get(locale);
    if (projectileTerm != null && normalizedEnglish.contains("projectile")) {
      assertThat(projectileTerm.matcher(inspectable).find())
          .describedAs("projectile terminology for %s:%s", locale, key)
          .isFalse();
    }
    Pattern tickTerm = FALSE_TICK_TERMS.get(locale);
    if (tickTerm != null && normalizedEnglish.contains("tick")) {
      assertThat(tickTerm.matcher(inspectable).find())
          .describedAs("tick terminology for %s:%s", locale, key)
          .isFalse();
    }
    Pattern slideTerm = FALSE_SLIDE_TERMS.get(locale);
    if (slideTerm != null && normalizedEnglish.contains("slide")) {
      assertThat(slideTerm.matcher(inspectable).find())
          .describedAs("slide terminology for %s:%s", locale, key)
          .isFalse();
    }
    if (locale.equals("zh_CN") || locale.equals("zh_TW")) {
      if (normalizedEnglish.contains("sprint-jump")) {
        assertThat(CHINESE_FALSE_SPRINT_JUMP_TERM.matcher(inspectable).find())
            .describedAs("Chinese sprint-jump terminology for %s:%s", locale, key)
            .isFalse();
      }
      if (normalizedEnglish.contains("guard")) {
        assertThat(CHINESE_FALSE_GUARD_TERM.matcher(inspectable).find())
            .describedAs("Chinese guard terminology for %s:%s", locale, key)
            .isFalse();
      }
      if (normalizedEnglish.contains(" times")) {
        assertThat(CHINESE_FALSE_OCCURRENCE_TERM.matcher(inspectable).find())
            .describedAs("Chinese occurrence terminology for %s:%s", locale, key)
            .isFalse();
      }
      if (normalizedEnglish.contains("slide")) {
        assertThat(CHINESE_FALSE_SLIDE_TERM.matcher(inspectable).find())
            .describedAs("Chinese slide terminology for %s:%s", locale, key)
            .isFalse();
      }
      if (!normalizedEnglish.contains("company")) {
        assertThat(CHINESE_FALSE_COMPANY_TERM.matcher(inspectable).find())
            .describedAs("Chinese company terminology for %s:%s", locale, key)
            .isFalse();
      }
      if (normalizedEnglish.contains("cast") || normalizedEnglish.contains("forge")) {
        assertThat(CHINESE_FALSE_CAST_TERM.matcher(inspectable).find())
            .describedAs("Chinese casting terminology for %s:%s", locale, key)
            .isFalse();
      }
    }
    if (english.toLowerCase(Locale.ROOT).contains("cooldown")) {
      Pattern cooldownArtifact = FORBIDDEN_COOLDOWN_TERMS.get(locale);
      assertThat(cooldownArtifact.matcher(inspectable).find())
          .describedAs("cooldown terminology for %s:%s", locale, key)
          .isFalse();
    }
    Pattern cjkArtifact = CJK_ASCII_ARTIFACTS.get(locale);
    if (cjkArtifact != null) {
      assertThat(cjkArtifact.matcher(inspectable).find())
          .describedAs("untranslated CJK text fragment for %s:%s", locale, key)
          .isFalse();
    }
    assertThat(hasIntroducedTripleRepeat(english, translated))
        .describedAs("repeated word artifact for %s:%s", locale, key)
        .isFalse();
    assertThat(pathologicalNgram(english, translated))
        .describedAs("repeated phrase artifact for %s:%s", locale, key)
        .isNull();
  }

  private static List<String> protocolTokens(String value) {
    return patternTokens(PROTOCOL_TOKEN, value);
  }

  private static String firstLine(AssertionError error) {
    String message = error.getMessage();
    if (message == null || message.isBlank()) {
      return error.getClass().getSimpleName();
    }
    int lineEnd = message.indexOf('\n');
    return lineEnd < 0 ? message : message.substring(0, lineEnd);
  }

  private static String delimiterIssue(String value) {
    StringBuilder stack = new StringBuilder();
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character == '(' || character == '[' || character == '{') {
        stack.append(character);
        continue;
      }
      if (character != ')' && character != ']' && character != '}') {
        continue;
      }
      if (stack.isEmpty()) {
        return "unexpected closing delimiter " + character;
      }
      char opening = stack.charAt(stack.length() - 1);
      if (!delimitersMatch(opening, character)) {
        return "mismatched delimiters " + opening + character;
      }
      stack.deleteCharAt(stack.length() - 1);
    }
    return stack.isEmpty() ? null : "unclosed delimiter " + stack.charAt(stack.length() - 1);
  }

  private static boolean delimitersMatch(char opening, char closing) {
    return opening == '(' && closing == ')'
        || opening == '[' && closing == ']'
        || opening == '{' && closing == '}';
  }

  private static List<String> patternTokens(Pattern pattern, String value) {
    List<String> tokens = new ArrayList<>();
    Matcher matcher = pattern.matcher(value);
    while (matcher.find()) {
      tokens.add(matcher.group());
    }
    tokens.sort(String::compareTo);
    return tokens;
  }

  private static boolean hasIntroducedTripleRepeat(String english, String translated) {
    return TRIPLE_WORD_REPEAT.matcher(translated).find() && !TRIPLE_WORD_REPEAT.matcher(english).find();
  }

  private static String pathologicalNgram(String english, String translated) {
    List<String> englishWords = words(english);
    List<String> translatedWords = words(translated);
    for (int size = 4; size <= 6; size++) {
      Map<String, Integer> englishCounts = ngramCounts(englishWords, size);
      Map<String, Integer> translatedCounts = ngramCounts(translatedWords, size);
      for (Map.Entry<String, Integer> entry : translatedCounts.entrySet()) {
        int sourceCount = englishCounts.getOrDefault(entry.getKey(), 0);
        if (entry.getValue() >= 4 && entry.getValue() > sourceCount) {
          return entry.getKey();
        }
      }
    }
    return null;
  }

  private static List<String> words(String value) {
    List<String> words = new ArrayList<>();
    Matcher matcher = WORD_TOKEN.matcher(value.toLowerCase(Locale.ROOT));
    while (matcher.find()) {
      words.add(matcher.group());
    }
    return words;
  }

  private static Map<String, Integer> ngramCounts(List<String> words, int size) {
    Map<String, Integer> counts = new HashMap<>();
    for (int index = 0; index + size <= words.size(); index++) {
      String ngram = String.join("\u0000", words.subList(index, index + size));
      counts.merge(ngram, 1, Integer::sum);
    }
    return counts;
  }
}
