# NCIP Peer Authentication

Set each HostLMS `ncip-peer-auth-mode` to `JWT_REQUIRED` or explicit `INSECURE`. Secured peers also
require `ncip-system-id`, `ncip-peer-issuer`, `ncip-peer-jwks-url` and `ncip-peer-audience`.

The configured NCIP SystemId identifies inbound HostLMS records and must equal JWT `sub` and NCIP
`FromSystemId`. DCB signs outbound tokens with its deployment-secret RSA JWK and publishes public keys
at `/peer-auth/.well-known/jwks.json`.

Adding the HostLMS approves its issuer/JWKS metadata. Review issuer or URL changes manually. Key
rotation at the same URL is automatic. Authentication failures are returned as NCIP Problem responses.

Remote JWKS retrieval is synchronous but runs on DCB's blocking executor. Connect and read timeouts
default to two and five seconds respectively and may be overridden per deployment:

```yaml
dcb:
  peer-auth:
    remote-jwks:
      connect-timeout: PT2S
      read-timeout: PT5S
```
