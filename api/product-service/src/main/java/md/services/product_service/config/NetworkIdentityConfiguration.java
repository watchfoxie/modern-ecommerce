package md.services.product_service.config;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.commons.util.InetUtilsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class NetworkIdentityConfiguration {

	@Bean
	@Primary
	InetUtils inetUtils(InetUtilsProperties properties, Environment environment) {
		return new ConfigurableInetUtils(properties, environment);
	}

	static final class ConfigurableInetUtils extends InetUtils {

		private final InetUtilsProperties properties;
		private final Environment environment;

		ConfigurableInetUtils(InetUtilsProperties properties, Environment environment) {
			super(properties);
			this.properties = properties;
			this.environment = environment;
		}

		@Override
		public HostInfo findFirstNonLoopbackHostInfo() {
			InetAddress address = resolveAddress();

			HostInfo hostInfo = new HostInfo();
			hostInfo.setOverride(true);
			hostInfo.setIpAddress(address.getHostAddress());
			hostInfo.setHostname(resolveHostname(address));

			return hostInfo;
		}

		@Override
		public InetAddress findFirstNonLoopbackAddress() {
			return resolveAddress();
		}

		private InetAddress resolveAddress() {
			InetAddress configuredAddress = resolveConfiguredIpAddress();
			if (configuredAddress != null) {
				return configuredAddress;
			}

			InetAddress preferredAddress = findPreferredAddress();
			if (preferredAddress != null) {
				return preferredAddress;
			}

			InetAddress localHostAddress = resolveLocalHostAddress();
			if (localHostAddress != null) {
				return localHostAddress;
			}

			throw new IllegalStateException(
					"Could not determine a routable IPv4 address for " + resolveApplicationName()
							+ ". Configure EUREKA_INSTANCE_IP_ADDRESS or adjust spring.cloud.inetutils.* settings.");
		}

		private InetAddress resolveConfiguredIpAddress() {
			String configuredIpAddress = trimToNull(environment.getProperty("EUREKA_INSTANCE_IP_ADDRESS"));
			if (configuredIpAddress != null) {
				return toConfiguredAddress(configuredIpAddress);
			}

			configuredIpAddress = trimToNull(environment.getProperty("eureka.instance.ip-address"));
			if (configuredIpAddress != null) {
				return toConfiguredAddress(configuredIpAddress);
			}

			return null;
		}

		private InetAddress toConfiguredAddress(String configuredIpAddress) {
			InetAddress address = toInetAddress(configuredIpAddress);
			if ((address == null) || !isUsableAddress(address)) {
				throw new IllegalStateException(
						"Configured EUREKA_INSTANCE_IP_ADDRESS/eureka.instance.ip-address '" + configuredIpAddress
								+ "' is not a routable non-loopback IPv4 address.");
			}

			return address;
		}

		private String resolveHostname(InetAddress address) {
			String configuredHostname = trimToNull(environment.getProperty("EUREKA_INSTANCE_HOSTNAME"));
			if (configuredHostname != null) {
				validateConfiguredHostname(configuredHostname);
				return configuredHostname;
			}

			configuredHostname = trimToNull(environment.getProperty("eureka.instance.hostname"));
			if (configuredHostname != null) {
				validateConfiguredHostname(configuredHostname);
				return configuredHostname;
			}

			String canonicalHostName = toDerivedHostname(address.getCanonicalHostName(), address);
			if (canonicalHostName != null) {
				return canonicalHostName;
			}

			String hostName = toDerivedHostname(address.getHostName(), address);
			if (hostName != null) {
				return hostName;
			}

			return address.getHostAddress();
		}

		private void validateConfiguredHostname(String configuredHostname) {
			InetAddress address = toInetAddress(configuredHostname);
			if ((address != null) && (address.isLoopbackAddress() || address.isLinkLocalAddress())) {
				throw new IllegalStateException(
						"Configured EUREKA_INSTANCE_HOSTNAME/eureka.instance.hostname '" + configuredHostname
								+ "' resolves to a non-routable address.");
			}
		}

		private String toDerivedHostname(String candidateHostname, InetAddress address) {
			String value = trimToNull(candidateHostname);
			if ((value == null) || "localhost".equalsIgnoreCase(value) || value.equals(address.getHostAddress())
					|| value.equalsIgnoreCase(resolveApplicationName()) || !value.contains(".")) {
				return null;
			}

			try {
				for (InetAddress resolvedAddress : InetAddress.getAllByName(value)) {
					if (address.equals(resolvedAddress)) {
						return value;
					}
				}
			}
			catch (UnknownHostException exception) {
				return null;
			}

			return null;
		}

		private InetAddress findPreferredAddress() {
			List<CandidateAddress> candidates = new ArrayList<>();

			try {
				Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
				if (interfaces == null) {
					return null;
				}

				while (interfaces.hasMoreElements()) {
					NetworkInterface candidateInterface = interfaces.nextElement();
					if (!isUsableInterface(candidateInterface)) {
						continue;
					}

					Enumeration<InetAddress> addresses = candidateInterface.getInetAddresses();
					while (addresses.hasMoreElements()) {
						InetAddress address = addresses.nextElement();
						if (!isUsableAddress(address)) {
							continue;
						}

						candidates.add(new CandidateAddress(
								address,
								getPreferredNetworkRank(address.getHostAddress()),
								getInterfacePenalty(candidateInterface),
								candidateInterface.getIndex()));
					}
				}
			}
			catch (SocketException exception) {
				return null;
			}

			return candidates.stream()
					.sorted(Comparator.comparingInt(CandidateAddress::preferredNetworkRank)
							.thenComparingInt(CandidateAddress::interfacePenalty)
							.thenComparingInt(CandidateAddress::interfaceIndex))
					.map(CandidateAddress::address)
					.findFirst()
					.orElse(null);
		}

		private InetAddress resolveLocalHostAddress() {
			try {
				InetAddress localHost = InetAddress.getLocalHost();
				return isUsableAddress(localHost) ? localHost : null;
			}
			catch (UnknownHostException exception) {
				return null;
			}
		}

		private boolean isUsableInterface(NetworkInterface candidateInterface) {
			try {
				if (!candidateInterface.isUp() || candidateInterface.isLoopback()) {
					return false;
				}
			}
			catch (SocketException exception) {
				return false;
			}

			return !matchesIgnoredInterface(candidateInterface.getName())
					&& !matchesIgnoredInterface(candidateInterface.getDisplayName());
		}

		private int getInterfacePenalty(NetworkInterface candidateInterface) {
			int penalty = 0;

			if (candidateInterface.isVirtual()) {
				penalty++;
			}

			if (isLikelyEphemeralAdapter(candidateInterface.getName())
					|| isLikelyEphemeralAdapter(candidateInterface.getDisplayName())) {
				penalty++;
			}

			return penalty;
		}

		private boolean isLikelyEphemeralAdapter(String interfaceName) {
			String value = trimToNull(interfaceName);
			if (value == null) {
				return false;
			}

			String normalized = value.toLowerCase();
			return normalized.contains("wsl") || normalized.contains("hyper-v") || normalized.contains("vethernet");
		}

		private boolean matchesIgnoredInterface(String interfaceName) {
			String value = trimToNull(interfaceName);
			if (value == null) {
				return false;
			}

			for (String pattern : orEmpty(properties.getIgnoredInterfaces())) {
				if (matchesPattern(value, pattern)) {
					return true;
				}
			}

			return false;
		}

		private boolean isUsableAddress(InetAddress address) {
			if (!(address instanceof Inet4Address) || address.isLoopbackAddress() || address.isLinkLocalAddress()) {
				return false;
			}

			if (properties.isUseOnlySiteLocalInterfaces() && !address.isSiteLocalAddress()) {
				return false;
			}

			return true;
		}

		private int getPreferredNetworkRank(String hostAddress) {
			List<String> preferredNetworks = orEmpty(properties.getPreferredNetworks());
			for (int index = 0; index < preferredNetworks.size(); index++) {
				String pattern = preferredNetworks.get(index);
				if (matchesPattern(hostAddress, pattern)) {
					return index;
				}
			}

			return Integer.MAX_VALUE;
		}

		private boolean matchesPattern(String value, String pattern) {
			String candidatePattern = trimToNull(pattern);
			if (candidatePattern == null) {
				return false;
			}

			try {
				return Pattern.compile(candidatePattern, Pattern.CASE_INSENSITIVE).matcher(value).find();
			}
			catch (PatternSyntaxException exception) {
				return false;
			}
		}

		private List<String> orEmpty(List<String> values) {
			return (values == null) ? Collections.emptyList() : values;
		}

		private InetAddress toInetAddress(String address) {
			String value = trimToNull(address);
			if (value == null) {
				return null;
			}

			try {
				return InetAddress.getByName(value);
			}
			catch (UnknownHostException exception) {
				return null;
			}
		}

		private String trimToNull(String value) {
			if (value == null) {
				return null;
			}

			String trimmed = value.trim();
			return trimmed.isEmpty() ? null : trimmed;
		}

		private String resolveApplicationName() {
			return environment.getProperty("spring.application.name", "the current service");
		}

		private record CandidateAddress(
				InetAddress address,
				int preferredNetworkRank,
				int interfacePenalty,
				int interfaceIndex) {
		}

	}

}
