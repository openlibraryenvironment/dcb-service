package org.olf.reshare.dcb.core;

import java.util.UUID;

import services.k_int.utils.UUIDUtils;

public interface Constants {
	public static interface UUIDs {
		public static final UUID NAMESPACE_DCB = UUIDUtils.nameUUIDFromNamespaceAndString(UUIDUtils.NAMESPACE_DNS, "org.olf.reshare");
	}
	public static interface Environment {
		public static final String DEMO = "demo";
	}
}
