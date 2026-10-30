FROM python:3.12-slim
WORKDIR /job
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY osm_parks.py refresh_parks.py ./
CMD ["python", "refresh_parks.py"]
