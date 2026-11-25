# Luxury Motors

Static showcase site (HTML, CSS, JavaScript) for a premium car showroom.
Runs as-is on GitHub Pages. Pages: signup, login, catalog (filter, sort, details), services, contact.

The forms are demo only: they validate input but send and save nothing. Catalog cars are sample listings.

## Java example (local learning only)
`java-local-example/Server.java` is a small Java 11 example (JDK built-in HTTP server, no libraries) showing signup/login with hashed passwords, a car list API and a contact endpoint.
It listens on localhost only, is not production-ready authentication, does not run on GitHub Pages, and the showcase pages do not use it. Do not expose it publicly or collect real user data with it.
Run locally: `java java-local-example/Server.java 8080`
