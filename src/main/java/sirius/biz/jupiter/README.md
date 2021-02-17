# Jupiter connectivity

Permits connecting to one or more [Jupiter](https://github.com/scireum/jupiter)
instances.

As many Jupiter tasks are "stateless" / can failover gracefully to
another node, one can specify a fallback instance per connection.

As Jupiter itself speaks the Redis RESP protocol, each instance has
to be defined as Redis pool in **redis.pools.NAME**. By default, we
will use **redis.pools.jupiter**.

See **component-biz.conf** for further settings.
