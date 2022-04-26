# Contributing

Prometheus uses GitHub to manage reviews of pull requests.

* If you have a trivial fix or improvement, go ahead and create a pull request,
  addressing (with `@...`) the maintainer of this repository (see
  [MAINTAINERS.md](MAINTAINERS.md)) in the description of the pull request.

* If you plan to do something more involved, first discuss your ideas
  on our [mailing list](https://groups.google.com/forum/?fromgroups#!forum/prometheus-developers).
  This will avoid unnecessary work and surely give you and us a good deal
  of inspiration.

### Formatting
- IDEs differ in how they format Java code. This can generate a lot of unrelated code change. To avoid this - we enforce specific formatting.
- Code is automatically formatted with [Spotify fmt maven-plugin](https://github.com/spotify/fmt-maven-plugin) whenever you run standard `mvn install`.
- CI builds will fail if code is not formatted that way.
- To simply run the formatter you can always run: `mvn fmt:format` (requires JVM > 11)
# Releasing

For release instructions, see [RELEASING](RELEASING.md).
