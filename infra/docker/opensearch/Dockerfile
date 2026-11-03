FROM opensearchproject/opensearch:2
# Analizator polski (ADR 0018): wtyczka analysis-stempel; indeks eskarga-akta używa filtra polish_stem.
RUN /usr/share/opensearch/bin/opensearch-plugin install --batch analysis-stempel
