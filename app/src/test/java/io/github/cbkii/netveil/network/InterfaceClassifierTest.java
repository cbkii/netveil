package io.github.cbkii.netveil.network;

import org.junit.Test;

import static io.github.cbkii.netveil.network.InterfaceClassifier.Kind;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InterfaceClassifierTest {
    @Test
    public void classifiesPixelAndCommonPhysicalInterfaces() {
        assertEquals(Kind.LOOPBACK, InterfaceClassifier.classify("lo"));
        assertEquals(Kind.WIFI, InterfaceClassifier.classify("wlan0"));
        assertEquals(Kind.CELLULAR, InterfaceClassifier.classify("rmnet_data0"));
        assertEquals(Kind.CELLULAR, InterfaceClassifier.classify("rmnet_ipa0"));
        assertEquals(Kind.CELLULAR, InterfaceClassifier.classify("ccmni0"));
        assertEquals(Kind.CELLULAR, InterfaceClassifier.classify("seth_lte8"));
        assertEquals(Kind.ETHERNET, InterfaceClassifier.classify("eth0"));
        assertEquals(Kind.ETHERNET, InterfaceClassifier.classify("rndis0"));
        assertEquals(Kind.OTHER_PHYSICAL, InterfaceClassifier.classify("dummy0"));
        assertEquals(Kind.OTHER_PHYSICAL, InterfaceClassifier.classify("bnep0"));
    }

    @Test
    public void recognizesClatWithoutTreatingItAsVpn() {
        assertEquals(Kind.CLAT, InterfaceClassifier.classify("v4-rmnet_data0"));
        assertEquals("rmnet_data0", InterfaceClassifier.normalizePhysicalName("v4-rmnet_data0"));
        assertFalse(InterfaceClassifier.isVpn("v4-rmnet_data0"));
    }

    @Test
    public void recognizesMaintainedVpnInterfaceSet() {
        String[] vpn = {
                "tun0", "tap0", "wg0", "ppp0", "ipsec0", "xfrm0", "utun3", "l2tp0",
                "gre0", "tailscale0", "ztyqb6mebi", "he-ipv6", "myvpn0", "custom_VPN_42",
                "if33", "IF0"
        };
        for (String name : vpn) assertTrue(name, InterfaceClassifier.isVpn(name));
        assertFalse(InterfaceClassifier.isVpn("ifb0"));
    }
}
