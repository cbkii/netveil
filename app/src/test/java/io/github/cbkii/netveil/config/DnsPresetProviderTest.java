package io.github.cbkii.netveil.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.cbkii.netveil.country.CountryCatalog;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DnsPresetProviderTest {
    @Test
    public void everySupportedCountryHasSmallValidPreset() {
        for (String country : CountryCatalog.codes()) {
            List<List<String>> sets = DnsPresetProvider.forCountry(country);
            assertTrue(country, sets.size() >= 2 && sets.size() <= 4);
            Set<String> seen = new HashSet<>();
            for (List<String> set : sets) {
                assertFalse(country, set.isEmpty());
                String canonical = String.join(",", set);
                assertTrue(country + " duplicate set", seen.add(canonical));
                for (String address : set) assertTrue(address, Ipv4.isLiteral(address));
            }
            assertEquals(sets.size(), Profile.parseDnsSets(DnsPresetProvider.format(country)).size());
        }
    }

    @Test
    public void unknownCountryHasNoPreset() {
        assertTrue(DnsPresetProvider.forCountry("ZZ").isEmpty());
    }
}
