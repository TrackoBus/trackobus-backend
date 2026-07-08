#!/bin/bash
set -e

# Create data directory
mkdir -p osrm-data

# Download Sri Lanka OSM data if not already present
if [ ! -f osrm-data/sri-lanka-latest.osm.pbf ]; then
    echo "Downloading Sri Lanka OSM data from Geofabrik..."
    curl -L http://download.geofabrik.de/asia/sri-lanka-latest.osm.pbf -o osrm-data/sri-lanka-latest.osm.pbf
else
    echo "OSM data already exists, skipping download."
fi

# Run OSRM extract
echo "Extracting profile (car.lua)..."
docker run --rm -v "$(pwd)/osrm-data:/data" osrm/osrm-backend osrm-extract -p /opt/car.lua /data/sri-lanka-latest.osm.pbf

# Run OSRM partition
echo "Partitioning map data..."
docker run --rm -v "$(pwd)/osrm-data:/data" osrm/osrm-backend osrm-partition /data/sri-lanka-latest.osrm

# Run OSRM customize
echo "Customizing graph..."
docker run --rm -v "$(pwd)/osrm-data:/data" osrm/osrm-backend osrm-customize /data/sri-lanka-latest.osrm

echo "OSRM Data processing completed successfully!"
