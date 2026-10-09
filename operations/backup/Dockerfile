FROM postgres:17-bookworm
RUN apt-get update && apt-get install -y --no-install-recommends python3 age \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /opt/backup
COPY operations/backup/backup.py operations/backup/test_backup.py ./
ENTRYPOINT ["python3", "/opt/backup/backup.py"]
