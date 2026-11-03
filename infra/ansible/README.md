# Role Ansible dla VM (T-053)

Produkcja i przedprodukcja działają na maszynach wirtualnych (D11): WildFly 41 z dystrybucją Operaton
(standalone-ha, 2 węzły), HAProxy, PostgreSQL 17 + Patroni + etcd, OpenSearch (3 węzły), usługi systemd dla
`docs-service` i workerów. Role powstaną w T-053 po decyzji o systemie operacyjnym VM i sposobie dystrybucji pakietów
[decyzja]. Wersje pakietów mają odpowiadać obrazom z `infra/docker`.
