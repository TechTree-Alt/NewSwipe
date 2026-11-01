package com.alternative_studios.newswipe.cjk;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 테스트용 작은 변환 사전. 형식: "읽기=글자:비용,글자:비용". */
final class CjkTestData {
    private CjkTestData() {
    }

    static byte[] bytes(String... lines) {
        Map<String, List<ConversionDictionary.Entry>> m = new LinkedHashMap<>();
        for (String line : lines) {
            String[] kv = line.split("=", 2);
            List<ConversionDictionary.Entry> es = new ArrayList<>();
            for (String e : kv[1].split(",")) {
                String[] tc = e.split(":");
                es.add(ConversionDictionary.entry(tc[0], Integer.parseInt(tc[1])));
            }
            m.put(kv[0], es);
        }
        return ConversionDictionary.build(m);
    }

    static ConversionDictionary dict(String... lines) {
        try {
            return ConversionDictionary.load(new ByteArrayInputStream(bytes(lines)));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    static final String[] ZH = {
            "ni=你:10,呢:20,泥:40", "hao=好:10,号:30", "ma=吗:10,妈:20", "ni'hao=你好:5", "hao'ma=号码:30",
            "xi=西:10,系:20", "an=安:10", "xian=先:10,县:20", "xi'an=西安:30", "wo=我:5", "men=们:10",
            "wo'men=我们:5", "zhong=中:10", "guo=国:10", "zhong'guo=中国:5", "he=和:10", "hui=会:10",
            "ni'hui=你会:20", "e=饿:40,额:45", "er=而:5,二:8", "bei=北:10", "jing=京:10", "bei'jing=北京:10",
            "bei'jin=背筋:90",
    };

    static final String[] JA = {
            "きょう=今日:10,京:40", "は=は:0,葉:60", "いい=いい:5,良い:80", "てんき=天気:20,転機:60",
            "でんしゃ=電車:20", "でん=電:50", "しゃ=者:80", "に=に:0,二:50", "のる=乗る:20",
            "ありがとう=有り難う:30", "ありがとうございます=有難うございます:40",
    };
}
