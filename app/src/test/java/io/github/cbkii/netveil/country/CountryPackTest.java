package io.github.cbkii.netveil.country;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

import java.util.List;

public final class CountryPackTest {
    private static final String[] PUBLIC_IPS = {
            "8.8.8.8", "1.1.1.1", "8.8.4.4", "1.0.0.1",
            "9.9.9.9", "149.112.112.112", "208.67.222.222", "208.67.220.220"
    };

    @Test
    public void parsesEveryRequiredCountry() throws Exception {
        CountryPack pack = CountryPack.parse(samplePack());
        for (String code : CountryPack.REQUIRED_COUNTRIES) {
            assertEquals(CountryPack.MIN_COUNTRY_CANDIDATES,
                    pack.candidates(code, false, false, 64).size());
        }
    }

    @Test
    public void highConfidenceFilterExcludesMediumAndLow() throws Exception {
        CountryPack pack = CountryPack.parse(samplePack());
        List<CountryPack.Candidate> values = pack.candidates("AU", true, false, 64);
        assertEquals(6, values.size());
        assertTrue(values.stream().allMatch(candidate -> "high".equals(candidate.confidence)));
    }

    @Test
    public void anonymityFilterCanBeOptedOut() throws Exception {
        CountryPack pack = CountryPack.parse(samplePack());
        List<CountryPack.Candidate> filtered = pack.candidates("US", false, true, 64);
        List<CountryPack.Candidate> unfiltered = pack.candidates("US", false, false, 64);
        assertEquals(CountryPack.MIN_COUNTRY_CANDIDATES - 1, filtered.size());
        assertEquals(CountryPack.MIN_COUNTRY_CANDIDATES, unfiltered.size());
        assertTrue(unfiltered.stream().anyMatch(CountryPack.Candidate::hasAnonymousSignal));
        assertTrue(filtered.stream().noneMatch(CountryPack.Candidate::hasAnonymousSignal));
    }

    @Test
    public void defaultLimitCapsRepresentativeList() throws Exception {
        CountryPack pack = CountryPack.parse(samplePack());
        assertEquals(4, pack.candidates("GB", false, false, 4).size());
    }

    @Test
    public void unsupportedSchemaFailsClosed() {
        assertThrows(JSONException.class,
                () -> CountryPack.parse("{\"schema\":2,\"generated_at\":\"2026-08-31T00:00:00Z\",\"countries\":{}}"));
    }

    @Test
    public void invalidTimestampFailsClosed() {
        assertThrows(JSONException.class,
                () -> CountryPack.parse(samplePack().replace("2026-08-31T00:00:00Z", "not-a-time")));
    }

    @Test
    public void privateOrSpecialCandidateFailsClosed() {
        String invalid = samplePack().replaceFirst("8\\.8\\.8\\.8", "192.168.1.2");
        assertThrows(JSONException.class, () -> CountryPack.parse(invalid));
        assertFalse(CountryPack.isPublicCandidate("100.64.1.1"));
        assertFalse(CountryPack.isPublicCandidate("203.0.113.5"));
        assertTrue(CountryPack.isPublicCandidate("8.8.8.8"));
    }

    @Test
    public void duplicateCandidateFailsClosed() {
        String invalid = samplePack().replaceFirst("1\\.1\\.1\\.1", "8.8.8.8");
        assertThrows(JSONException.class, () -> CountryPack.parse(invalid));
    }

    @Test
    public void missingBooleanOrProvenanceFailsClosed() {
        String missingFlag = samplePack().replaceFirst("\"known_tor\":false,", "");
        assertThrows(JSONException.class, () -> CountryPack.parse(missingFlag));
        String missingProvider = samplePack().replaceFirst("\"provider\":\"Fixture ISP\",", "");
        assertThrows(JSONException.class, () -> CountryPack.parse(missingProvider));
    }

    @Test
    public void generatedTimestampOrdersCacheAndBundle() throws Exception {
        CountryPack older = CountryPack.parse(samplePack().replace(
                "2026-08-31T00:00:00Z", "2026-08-30T00:00:00Z"));
        CountryPack newer = CountryPack.parse(samplePack());
        assertTrue(newer.isAtLeastAsNewAs(older));
        assertFalse(older.isAtLeastAsNewAs(newer));
    }

    private static String samplePack() {
        String au = countryRows(1, false);
        String us = countryRows(-1, true);
        String generic = countryRows(-1, false);
        return "{\"schema\":1,\"generated_at\":\"2026-08-31T00:00:00Z\",\"countries\":{"
                + "\"AU\":" + au + ",\"US\":" + us + ",\"GB\":" + generic
                + ",\"ID\":" + generic + ",\"FR\":" + generic + "}}";
    }

    private static String countryRows(int mediumIndex, boolean anonymousLast) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < PUBLIC_IPS.length; i++) {
            if (i > 0) out.append(',');
            String confidence = i == mediumIndex ? "medium" : (i == 7 ? "low" : "high");
            boolean vpn = anonymousLast && i == PUBLIC_IPS.length - 1;
            out.append(candidate(PUBLIC_IPS[i], confidence, vpn, false, false));
        }
        return out.append(']').toString();
    }

    private static String candidate(String ip, String confidence, boolean vpn,
                                    boolean proxy, boolean tor) {
        return "{\"ipv4\":\"" + ip + "\",\"confidence\":\"" + confidence
                + "\",\"known_vpn\":" + vpn + ",\"known_proxy\":" + proxy
                + ",\"known_tor\":" + tor + ",\"provider\":\"Fixture ISP\",\"asn\":64500}";
    }
}
