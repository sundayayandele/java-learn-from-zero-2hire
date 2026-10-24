#!/usr/bin/env sh
# Create local test users in the Keycloak realm started by compose.yaml (local development only).
#   DEV_USER_PASSWORD=<choose one> ./scripts/dev-users.sh
# Creates: alice (CUSTOMER), bob (CUSTOMER), olu (OPS), ada (ADMIN), all with DEV_USER_PASSWORD.
set -eu
: "${DEV_USER_PASSWORD:?set DEV_USER_PASSWORD}"
: "${KEYCLOAK_ADMIN_PASSWORD:?set KEYCLOAK_ADMIN_PASSWORD (same as in .env)}"
kc() { docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh "$@"; }
kc config credentials --server http://localhost:8081 --realm master --user admin --password "$KEYCLOAK_ADMIN_PASSWORD"
for pair in alice:CUSTOMER bob:CUSTOMER olu:OPS ada:ADMIN; do
  user=${pair%%:*}; role=${pair##*:}
  kc create users -r itl -s username="$user" -s enabled=true -s email="$user@example.test" \
     -s firstName="$user" -s lastName=Test -s emailVerified=true || true
  kc set-password -r itl --username "$user" --new-password "$DEV_USER_PASSWORD"
  kc add-roles -r itl --uusername "$user" --rolename "$role"
done
echo "Users created. Get a token:"
echo "  curl -s -d client_id=itl-cli -d grant_type=password -d username=alice -d password=\$DEV_USER_PASSWORD \\"
echo "       -d scope='openid api.read api.write' http://localhost:8081/realms/itl/protocol/openid-connect/token"
