package me.snodrop.booster.istio.ab;

public class ServiceResponse {

	private String response;

    public ServiceResponse(String response) {
    	this.setResponse(response);
    }

	public String getResponse() {
		return response;
	}

	public void setResponse(String response) {
		this.response = response;
	}
}
