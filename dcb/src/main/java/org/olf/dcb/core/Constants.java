package org.olf.dcb.core;

import java.util.UUID;

import services.k_int.utils.UUIDUtils;

public interface Constants {
	public static interface UUIDs {
		public static final UUID NAMESPACE_DCB = UUIDUtils.nameUUIDFromNamespaceAndString(UUIDUtils.NAMESPACE_DNS, "org.olf.dcb");
		public static final UUID NAMESPACE_AGENCIES = UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "Agency");
		public static final UUID NAMESPACE_HOSTLMS = UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "HostLms");
		public static final UUID NAMESPACE_LOCATION = UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "Location");
		public static final UUID NAMESPACE_MAPPINGS = UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "Mappings");
		public static final UUID NAMESPACE_ALARMS = UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "Alarms");
	}
	public static interface Environment {
		public static final String DEMO = "demo";
	}
}
