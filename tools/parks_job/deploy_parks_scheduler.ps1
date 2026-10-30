# Deploys the Street Gymnastics parks refresh as a Cloud Run Job started by Cloud Scheduler every three months, the same
# way as JustMaps' event importer (justmaps/tools/deploy_event_scheduler.ps1). Idempotent: run it again to update.
# Uses your own gcloud login (gcloud auth login); nothing runs on this PC after deployment.
#   .\deploy_parks_scheduler.ps1 -Project <firebase project id>
param(
    [Parameter(Mandatory = $true)][string]$Project,
    [string]$Region = "europe-west1",
    [string]$Schedule = "0 5 1 1,4,7,10 *",
    [string]$TimeZone = "Europe/Athens"
)

$ErrorActionPreference = "Stop"
$Gcloud = (Get-Command gcloud.cmd -ErrorAction Stop).Source
# Not $Args: that's PowerShell's automatic variable, empty inside the script blocks below.
$Flags = @("--project=$Project", "--quiet")
$Repository = "streetgym-jobs"
$Image = "$Region-docker.pkg.dev/$Project/$Repository/parks-refresh:latest"
$Job = "streetgym-parks-refresh"
$Scheduler = "streetgym-parks-quarterly"
$Bucket = "$Project-parks"
$Account = "streetgym-parks"
$ServiceAccount = "$Account@$Project.iam.gserviceaccount.com"

function Exists([scriptblock]$Describe) {
    $ErrorActionPreference = "SilentlyContinue"
    & $Describe *> $null
    return $LASTEXITCODE -eq 0
}

& $Gcloud services enable run.googleapis.com cloudscheduler.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com storage.googleapis.com iam.googleapis.com @Flags

if (-not (Exists { & $Gcloud iam service-accounts describe $ServiceAccount @Flags })) {
    & $Gcloud iam service-accounts create $Account --display-name="Street Gymnastics parks refresh" @Flags
}
if (-not (Exists { & $Gcloud artifacts repositories describe $Repository --location=$Region @Flags })) {
    & $Gcloud artifacts repositories create $Repository --repository-format=docker --location=$Region --description="Street Gymnastics scheduled jobs" @Flags
}
# The parks file is public (the app downloads it without signing in); only the job's account writes it.
if (-not (Exists { & $Gcloud storage buckets describe "gs://$Bucket" @Flags })) {
    & $Gcloud storage buckets create "gs://$Bucket" --location=$Region --uniform-bucket-level-access @Flags
}
& $Gcloud storage buckets add-iam-policy-binding "gs://$Bucket" --member=allUsers --role=roles/storage.objectViewer @Flags
& $Gcloud storage buckets add-iam-policy-binding "gs://$Bucket" --member="serviceAccount:$ServiceAccount" --role=roles/storage.objectAdmin @Flags

# gcloud writes progress to stderr, so check the exit code: a failed build must never redeploy the previous image.
Push-Location $PSScriptRoot
try {
    & $Gcloud builds submit --tag=$Image @Flags
    if ($LASTEXITCODE -ne 0) { throw "Image build failed (exit $LASTEXITCODE); job left unchanged" }
} finally {
    Pop-Location
}
& $Gcloud run jobs deploy $Job --image=$Image --region=$Region --service-account=$ServiceAccount --task-timeout=3h --memory=1Gi --cpu=1 --max-retries=1 --set-env-vars="PARKS_BUCKET=$Bucket" @Flags
& $Gcloud run jobs add-iam-policy-binding $Job --region=$Region --member="serviceAccount:$ServiceAccount" --role="roles/run.invoker" @Flags

$Uri = "https://run.googleapis.com/v2/projects/$Project/locations/$Region/jobs/$Job`:run"
$Common = @("--location=$Region", "--schedule=$Schedule", "--time-zone=$TimeZone", "--uri=$Uri", "--http-method=POST",
    "--oauth-service-account-email=$ServiceAccount", "--oauth-token-scope=https://www.googleapis.com/auth/cloud-platform",
    "--description=Refresh Street Gymnastics parks every three months")
if (Exists { & $Gcloud scheduler jobs describe $Scheduler --location=$Region @Flags }) {
    & $Gcloud scheduler jobs update http $Scheduler @Common @Flags
} else {
    & $Gcloud scheduler jobs create http $Scheduler @Common @Flags
}

Write-Output "Deployed $Job and scheduled $Scheduler at $Schedule ($TimeZone)."
Write-Output "Parks URL for firebase.xml (parks_url): https://storage.googleapis.com/$Bucket/parks/spots.tsv.gz"
Write-Output "First run now: gcloud run jobs execute $Job --region=$Region --project=$Project --wait"
