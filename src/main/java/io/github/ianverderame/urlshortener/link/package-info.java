/**
 * Creating and looking up short links: the create API, the destination policy it enforces,
 * short-code generation, and link storage. Other packages read links through {@link LinkLookup};
 * creating one goes through this package's own service. Depends on {@code security} for the API-key
 * scheme name and on {@code web} for {@code ProblemException}.
 */
package io.github.ianverderame.urlshortener.link;
