package io.github.cbkii.netveil.network;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PropertyMaskPolicyTest {
    @Test
    public void javaProxyMatchingIsExact() {
        assertTrue(PropertyMaskPolicy.isJavaProxyKey("http.proxyHost"));
        assertTrue(PropertyMaskPolicy.isJavaProxyKey("socksProxyPort"));
        assertFalse(PropertyMaskPolicy.isJavaProxyKey("vendor.proxy_host_backup"));
        assertFalse(PropertyMaskPolicy.isJavaProxyKey("my.vpn.proxy"));
    }

    @Test
    public void androidNetworkPropertiesAreAllowlisted() {
        assertEquals(PropertyMaskPolicy.AndroidPropertyKind.DNS,
                PropertyMaskPolicy.classifyAndroid("net.dns1"));
        assertEquals(PropertyMaskPolicy.AndroidPropertyKind.DNS,
                PropertyMaskPolicy.classifyAndroid("dhcp.wlan0.dns4"));
        assertEquals(PropertyMaskPolicy.AndroidPropertyKind.GATEWAY,
                PropertyMaskPolicy.classifyAndroid("dhcp.rmnet_data0.gateway"));
        assertEquals(PropertyMaskPolicy.AndroidPropertyKind.IPV4,
                PropertyMaskPolicy.classifyAndroid("dhcp.v4-rmnet_data0.ipaddress"));
        assertEquals(PropertyMaskPolicy.AndroidPropertyKind.NONE,
                PropertyMaskPolicy.classifyAndroid("vendor.anything.vpn.mode"));
    }

    @Test
    public void dnsIndexMatchesConfiguredSlot() {
        assertEquals(0, PropertyMaskPolicy.dnsIndex("net.dns1"));
        assertEquals(3, PropertyMaskPolicy.dnsIndex("dhcp.wlan0.dns4"));
        assertEquals(-1, PropertyMaskPolicy.dnsIndex("dhcp.wlan0.gateway"));
    }
}
