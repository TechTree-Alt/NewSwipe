package com.alternative_studios.newswipe.cjk;

import java.util.List;

/** 읽기 → 후보. 앞쪽 후보일수록 먼저 보인다. */
public interface Converter {
    List<Candidate> convert(String reading, int max);
}
