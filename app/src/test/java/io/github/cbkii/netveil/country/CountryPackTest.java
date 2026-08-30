package io.github.cbkii.netveil.country;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

import java.util.List;

public final class CountryPackTest {
    @Test
    public void parsesEveryRequiredCountry() throws Exception {
        CountryPack pack = CountryPack.parse(samplePack());
        for (String code : CountryPack.REQUIRED_COUNTRIES) {
            assertFalse(pack.candidates(code, false, false, 12).isEmpty());
        }
    }

    @Test
    public void highConfidenceFilterExcludesMedium() throws Exception {
        CountryPack pack = CountryPack.parse(samplePack());
        List<CountryPack.Candidate> values = pack.candidates("AU", true, false, 12);
        assertEquals(1, values.size());
        assertEquals("8.8.8.8", values.get(0).ipv4);
        assertEquals("high", values.get(0).confidence);
    }

    @Test
    public void anonymityFilterIsOnByPolicyButCanBeOptedOut() throws Exception {
        CountryPack pack = CountryPack.parse(samplePack());
        List<CountryPack.Candidate> filtered = pack.candidates("US", false, true, 12);
        assertEquals(1, filtered.size());
        assertEquals("8.8.4.4", filtered.get(0).ipv4);

        List<CountryPack.Candidate> unfiltered = pack.candidates("US", false, false, 12);
        assertEquals(2, unfiltered.size());
        assertTrue(unfiltered.get(1).hasAnonymousSignal());
    }

    @Test
    public void canonicalDuplicatesAreRemoved() throws Exception {
        CountryPack pack = CountryPack.parse(samplePack());
        assertEquals(2, pack.candidates("AU", false, false, 12).size());
    }

    @Test
    public void unsupportedSchemaFailsClosed() {
        assertThrows(JSONException.class,
                () -> CountryPack.parse("{\"schema\":2,\"generated_at\":\"x\",\"countries\":{}}"));
    }

    private static String samplePack() {
        String au = "["
                + candidate("8.8.8.8", "high", false, false, false) + ","
                + candidate("1.1.1.1", "medium", false, false, false) + ","
                + candidate("8.8.8.8", "high", false, false, false) + "]";
        String us = "["
                + candidate("8.8.4.4", "high", false, false, false) + ","
                + candidate("9.9.9.9", "high", true, false, false) + "]";
        String generic = "[" + candidate("1.0.0.1", "high", false, false, false) + "]";
        return "{\"schema\":1,\"generated_at\":\"2026-08-31T00:00:00Z\",\"countries\":{"
                + "\"AU\":" + au + ",\"US\":" + us + ",\"GB\":" + generic
                + ",\"ID\":" + generic + ",\"FR\":" + generic + "}}";
    }

    private static String candidate(String ip, String confidence, boolean vpn,
                                    boolean proxy, boolean tor) {
        return "{\"ipv4\":\"" + ip + "\",\"confidence\":\"" + confidence
                + "\",\"known_vpn\":" + vpn + ",\"known_proxy\":" + proxy
                + ",\"known_tor\":" + tor + "}";
    }
}
