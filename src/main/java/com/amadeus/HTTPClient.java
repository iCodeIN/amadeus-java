package com.amadeus;

import com.amadeus.client.AccessToken;
import com.amadeus.exceptions.NetworkException;
import com.amadeus.exceptions.ResponseException;
import com.amadeus.resources.Resource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.logging.Logger;
import lombok.Getter;

/**
 * The HTTP part of the Amadeus API client. See the Amadeus class for
 * more details on initialization.
 */
public class HTTPClient {
  // A cached copy of the Access Token. It will auto refresh for every bearerToken (if needed)
  protected AccessToken accessToken = new AccessToken(this);

  /**
   * The configuration for this API client.
   */
  private @Getter Configuration configuration;

  protected HTTPClient(Configuration configuration) {
    this.configuration = configuration;
  }

  /**
   * A helper module for making generic GET requests calls. It is used by
   * every namespaced API GET method.
   *
   * @see Amadeus#get(String, Params)
   */
  public Response get(String path) throws ResponseException {
    return request("GET", path, null);
  }

  /**
   * <p>
   *   A helper module for making generic GET requests calls. It is used by
   *   every namespaced API GET method.
   * </p>
   *
   * <pre>
   *   amadeus.referenceData.urls.checkinLinks.get(Params.with("airline", "1X"));
   * </pre>
   *
   * <p>
   *   It can be used to make any generic API call that is automatically
   *   authenticated using your API credentials:
   * </p>
   *
   * <pre>
   *    amadeus.get("/v2/reference-data/urls/checkin-links", Params.with("airline", "1X"));
   * </pre>
   *
   * @param path The full path for the API call
   * @param params The optional GET params to pass to the API
   * @return a Response object containing the status code, body, and parsed data.
   */
  public Response get(String path, Params params) throws ResponseException {
    return request("GET", path, params);
  }

  /**
   * A helper module for making generic POST requests calls. It is used by
   * every namespaced API POST method.
   *
   * @see Amadeus#post(String, Params)
   */
  public Response post(String path) throws ResponseException {
    return request("POST", path, null);
  }

  /**
   * <p>
   *   A helper module for making generic POST requests calls. It is used by
   *   every namespaced API POST method.
   * </p>
   *
   * <pre>
   *   amadeus.foo.bar.post(Params.with("airline", "1X"));
   * </pre>
   *
   * <p>
   *   It can be used to make any generic API call that is automatically
   *   authenticated using your API credentials:
   * </p>
   *
   * <pre>
   *    amadeus.post("/v1/foo/bar", Params.with("airline", "1X"));
   * </pre>
   *
   * @param path The full path for the API call
   * @param params The optional POST params to pass to the API
   * @return a Response object containing the status code, body, and parsed data.
   */
  public Response post(String path, Params params) throws ResponseException {
    return request("POST", path, params);
  }

  /**
   * A generic method for making any authenticated or unauthenticated request,
   * passing in the bearer token explicitly. Used primarily by the
   * AccessToken to get the first AccessToken.
   *
   * @hides as only used internally
   */
  public Response unauthenticatedRequest(String verb, String path, Params params,
                                         String bearerToken) throws ResponseException {
    Request request = buildRequest(verb, path, params, bearerToken);
    log(request);
    return execute(request);
  }

  /**
   * Fetches the previous page for a given response.
   * @param response a response object previously received for which includes an array of data
   * @return a new response of data
   * @throws ResponseException if the page could not be found
   */
  public Response previous(Response response) throws ResponseException {
    return page("previous", response);
  }

  /**
   * Fetches the previous page for a given response.
   * @param resource one of the responses previously received from an API call
   * @return a new array of resources of the same type
   * @throws ResponseException if the page could not be found
   */
  public Resource[] previous(Resource resource) throws ResponseException {
    return page("previous", resource);
  }

  /**
   * Fetches the next page for a given response.
   * @param response a response object previously received for which includes an array of data
   * @return a new response of data
   * @throws ResponseException if the page could not be found
   */
  public Response next(Response response) throws ResponseException {
    return page("next", response);
  }

  /**
   * Fetches the next page for a given response.
   * @param resource one of the responses previously received from an API call
   * @return a new array of resources of the same type
   * @throws ResponseException if the page could not be found
   */
  public Resource[] next(Resource resource) throws ResponseException {
    return page("next", resource);
  }

  /**
   * Fetches the first page for a given response.
   * @param response a response object previously received for which includes an array of data
   * @return a new response of data
   * @throws ResponseException if the page could not be found
   */
  public Response first(Response response) throws ResponseException {
    return page("first", response);
  }

  /**
   * Fetches the first page for a given response.
   * @param resource one of the responses previously received from an API call
   * @return a new array of resources of the same type
   * @throws ResponseException if the page could not be found
   */
  public Resource[] first(Resource resource) throws ResponseException {
    return page("first", resource);
  }

  /**
   * Fetches the last page for a given response.
   * @param response a response object previously received for which includes an array of data
   * @return a new response of data
   * @throws ResponseException if the page could not be found
   */
  public Response last(Response response) throws ResponseException {
    return page("last", response);
  }

  /**
   * Fetches the last page for a given response.
   * @param resource one of the responses previously received from an API call
   * @return a new array of resources of the same type
   * @throws ResponseException if the page could not be found
   */
  public Resource[] last(Resource resource) throws ResponseException {
    return page("last", resource);
  }

  // A generic method for making requests of any verb.
  protected Response request(String verb, String path, Params params) throws ResponseException {
    return unauthenticatedRequest(verb, path, params, accessToken.getBearerToken());
  }

  // Builds a request
  protected Request buildRequest(String verb, String path, Params params, String bearerToken) {
    return new Request(verb, path, params, bearerToken, this);
  }

  // A simple log that only triggers if we are in debug mode
  private void log(Object object) {
    if (getConfiguration().getLogLevel() == "debug") {
      Logger logger = getConfiguration().getLogger();
      logger.info(object.toString());
    }
  }

  // Executes a request and return a Response
  private Response execute(Request request) throws ResponseException {
    Response response = new Response(fetch(request));
    response.parse(this);
    log(response);
    response.detectError(this);
    return response;
  }


  // Tries to make an API call. Raises an error if it can't complete the call.
  private Request fetch(Request request) throws NetworkException {
    try {
      request.establishConnection();
      write(request);
    } catch (IOException e) {
      throw new NetworkException(new Response(request));
    }
    return request;
  }

  // Writes the parameters to the request.
  private void write(Request request) throws IOException {
    if (request.getVerb() == "POST" && request.getParams() != null) {
      OutputStream os = request.getConnection().getOutputStream();
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
      writer.write(request.getParams().toQueryString());
      writer.flush();
      writer.close();
      os.close();
    }
  }

  /**
   * Fetches the response for another page.
   * @hide as ony used internally
   */
  protected Response page(String pageName, Response response) throws ResponseException {
    try {
      String[] parts = response.getResult().get("meta").getAsJsonObject()
              .get("links").getAsJsonObject().get(pageName).getAsString().split("=");

      String pageNumber = parts[parts.length - 1];

      Request request = response.getRequest();
      Params params = (Params) request.getParams().clone();
      params.put("page[offset]", pageNumber);

      return request(request.getVerb(), request.getPath(), params);
    } catch (NullPointerException e) {
      return null;
    }
  }

  /**
   * Fetches the response for another page.
   * @hide as ony used internally
   */
  protected Resource[] page(String pageName, Resource resource) throws ResponseException {
    Response response = page(pageName, resource.getResponse());
    return Resource.fromArray(response, resource.getDeSerializationClass());
  }
}