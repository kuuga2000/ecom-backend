FROM docker:29-cli AS docker-cli

FROM maven:3.9.11-eclipse-temurin-25

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl sudo \
    && rm -rf /var/lib/apt/lists/* \
    && usermod --login guosong --home /home/guosong --move-home ubuntu \
    && groupmod --new-name guosong ubuntu \
    && echo 'guosong ALL=(root) NOPASSWD:ALL' > /etc/sudoers.d/guosong \
    && chmod 0440 /etc/sudoers.d/guosong \
    && install -d -o guosong -g guosong /home/guosong/.local/bin /home/guosong/.m2 /home/guosong/.codex

COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker-cli /usr/local/libexec/docker/cli-plugins/docker-compose /usr/local/libexec/docker/cli-plugins/docker-compose

RUN bash -o pipefail -c 'curl -fsSL https://chatgpt.com/codex/install.sh \
    | env CODEX_NON_INTERACTIVE=1 CODEX_INSTALL_DIR=/usr/local/bin sh' \
    && rm /usr/local/bin/codex \
    && install -o guosong -g guosong -m 0755 /root/.codex/packages/standalone/current/bin/codex /home/guosong/.local/bin/codex \
    && rm -rf /root/.codex

ENV PATH="/home/guosong/.local/bin:${PATH}"

USER guosong
WORKDIR /workspaces/ecom-backend
