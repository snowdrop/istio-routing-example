package me.snowdrop.booster.istio.ab;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class ClientController {

	private final RestTemplate restTemplate;

	private final String serviceName;

	public ClientController(RestTemplate restTemplate, @Value("${service.data.name}") String serviceName) {
		this.restTemplate = restTemplate;
		this.serviceName = serviceName;
	}

	@RequestMapping("/request-data")
	public String serial() {

		final String response = restTemplate.getForObject(getURI(this.serviceName, "data"),
				String.class);

		return response;
	}

	private URI getURI(String serviceName, String path) {
		return URI.create(String.format("http://%s/%s", serviceName, path));
	}
}
