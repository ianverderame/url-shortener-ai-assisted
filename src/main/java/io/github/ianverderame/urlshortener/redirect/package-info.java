/**
 * The public, unauthenticated redirect from a short code to its destination: the service's hot
 * path. Depends on {@code link} to resolve the code and on {@code analytics} to record the click.
 */
package io.github.ianverderame.urlshortener.redirect;
