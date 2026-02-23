package org.olf.dcb.core.interaction.polaris;

class Paths {
	String patronItemCheckOut(String localPatronBarcode) {
		return publicPapiService("/patron/%s/itemsout".formatted(localPatronBarcode));
	}

	String itemsByBibId(Integer bibId) {
		return protectedPapiService("/string/synch/items/bibid/" + bibId);
	}

	String getItem(Integer itemId) {
		return applicationServices("/itemrecords/" + itemId);
	}

	String getItemByBarcode(Integer localItemId) {
		return applicationServices("/barcodes/items/" + localItemId);
	}

	String blocksSummary(String patronId) {
		return applicationServices("/patrons/%s/blockssummary".formatted(patronId));
	}

	String localRequests(Integer patronId) {
		return applicationServices("/patrons/%s/requests/local".formatted(patronId));
	}

	String getHold(String holdId) {
		return applicationServices("/holds/" + holdId);
	}

	String getBib(Integer bibId) {
		return applicationServices("/bibliographicrecords/%s*".formatted(bibId));
	}

	String patronById(String patronId) {
		return applicationServices("/patrons/%s".formatted(patronId));
	}

	String patronByBarcode(String patronBarcode) {
		return this.publicPapiService("/patron/" + patronBarcode);
	}

	String workflow() {
		return applicationServices("/workflow");
	}

	String createPatron() {
		return publicPapiService("/patron");
	}

	String applicationServices(String path) {
		return baseApplicationServices("/polaris/73/1" + path);
	}

	String baseApplicationServices(String path) {
		return "/polaris.applicationservices/api/v1/eng/20%s".formatted(path);
	}

	String protectedPapiService(String subPath) {
		return papiService("protected", subPath);
	}

	String publicPapiService(String subPath) {
		return papiService("public", subPath);
	}

	String papiService(String scope, String subPath) {
		return "/PAPIService/REST/%s/v1/1033/100/1%s".formatted(scope, subPath);
	}
}
