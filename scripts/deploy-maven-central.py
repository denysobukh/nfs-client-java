#!/usr/bin/env python3
"""Upload a tagged release with local GPG and record the result on GitHub."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REPO = 'denysobukh/nfs-client-java'
ENVIRONMENT = 'maven-central'
PORTAL = 'https://central.sonatype.com/publishing/deployments'


def run(*args, capture=False):
    return subprocess.run(args, cwd=ROOT, check=True, text=True,
                          stdout=subprocess.PIPE if capture else None).stdout


def api(endpoint, payload):
    result = subprocess.run(['gh', 'api', '--method', 'POST', endpoint, '--input', '-'],
                            cwd=ROOT, input=json.dumps(payload), text=True,
                            stdout=subprocess.PIPE, check=True)
    return json.loads(result.stdout)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('tag', help='Existing release tag checked out locally, e.g. v1.1.1')
    parser.add_argument('--check', action='store_true',
                        help='Validate checkout and build, without uploading or creating a deployment')
    args = parser.parse_args()
    if not args.tag.startswith('v'):
        parser.error('Expected a version tag such as v1.1.1')
    if run('git', 'status', '--porcelain', capture=True).strip():
        parser.error('Commit or stash changes before deploying a tagged release')
    head = run('git', 'rev-parse', 'HEAD', capture=True).strip()
    tagged = run('git', 'rev-parse', '--verify', f'refs/tags/{args.tag}^{{commit}}', capture=True).strip()
    if tagged != head:
        parser.error('Check out the requested tag before deploying')
    remote = run('git', 'ls-remote', f'https://github.com/{REPO}.git',
                 f'refs/tags/{args.tag}', f'refs/tags/{args.tag}^{{}}', capture=True)
    if not any(line.split()[0] == head for line in remote.splitlines()):
        parser.error('The tag must be pushed to GitHub and match this checkout')

    run('./gradlew', '--no-daemon', 'clean', 'build', 'generatePomFileForMavenPublication')
    pom = ET.parse(ROOT / 'build/publications/maven/pom-default.xml').getroot()
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    version = pom.find('m:version', ns).text
    if args.tag != f'v{version}':
        parser.error(f'Tag does not match POM version {version}')
    if args.check:
        print('Checkout, remote tag, build and publication version verified. No deployment created.')
        return

    # Credentials and GPG configuration come from the local Gradle user home.
    # Nothing is copied to GitHub. This uploads for Portal review; it does not
    # automatically release the Maven Central deployment.
    deployment = api(f'repos/{REPO}/deployments', {
        'ref': head, 'task': 'deploy', 'auto_merge': False, 'required_contexts': [],
        'environment': ENVIRONMENT, 'production_environment': True,
        'description': f'Upload {args.tag} to Central Portal for review',
        'payload': {'tag': args.tag, 'version': version},
    })
    endpoint = f'repos/{REPO}/deployments/{deployment["id"]}/statuses'
    api(endpoint, {'state': 'in_progress', 'environment_url': PORTAL,
                   'description': 'Signing locally and uploading to Central Portal'})
    try:
        run('./gradlew', '--no-daemon', 'publishToMavenCentral',
            '-PmavenCentralAutomaticPublishing=false')
    except (subprocess.CalledProcessError, KeyboardInterrupt):
        api(endpoint, {'state': 'failure', 'environment_url': PORTAL,
                       'description': 'Local upload failed or was interrupted; inspect Central Portal before retrying'})
        raise
    api(endpoint, {'state': 'success', 'environment_url': PORTAL, 'auto_inactive': False,
                   'description': 'Uploaded for Central Portal review; public release is a separate step'})
    print(f'Uploaded {args.tag}. Review and publish the deployment at {PORTAL}')


if __name__ == '__main__':
    try:
        main()
    except subprocess.CalledProcessError as error:
        sys.exit(error.returncode)
