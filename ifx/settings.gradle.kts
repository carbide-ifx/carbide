rootProject.name = "ifx"

include(
    ":context",
    ":host",
    ":naming",
    ":logging",
    ":proxy:contract",
    ":proxy:utility",
    ":service",
    ":transport",
    ":stdlib",
    ":test",
    ":protocol:contract",
    ":protocol:rsocket-reflect"
)
