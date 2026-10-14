/**
 * HTTP concerns shared by every endpoint: RFC 7807 problem responses, including errors Tomcat
 * answers itself, and the request-body size limit. Controllers live in their feature packages, not
 * here. Depends on the feature packages for the exceptions it maps to problem responses.
 */
package io.github.ianverderame.urlshortener.web;
