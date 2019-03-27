# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.

from __future__ import print_function

import arrow
import datetime
import json
import os
import taskcluster

DEFAULT_EXPIRES_IN = '1 year'


class TaskBuilder(object):
    def __init__(
        self, task_id, repo_url, branch, commit, owner, source, scheduler_id,
        tasks_priority='lowest'
    ):
        self.task_id = task_id
        self.repo_url = repo_url
        self.branch = branch
        self.commit = commit
        self.owner = owner
        self.source = source
        self.scheduler_id = scheduler_id
        self.tasks_priority = tasks_priority

    def craft_assemble_release_task(self, apks, is_staging=False):
        artifacts = {
            'public/{}'.format(os.path.basename(apk)): {
                "type": 'file',
                "path": apk,
                "expires": taskcluster.stringDate(taskcluster.fromNow(DEFAULT_EXPIRES_IN)),
            }
            for apk in apks
        }

        sentry_secret = '{}project/mobile/fenix/sentry'.format(
            'garbage/staging/' if is_staging else ''
        )
        leanplum_secret = '{}project/mobile/fenix/leanplum'.format(
            'garbage/staging/' if is_staging else ''
        )

        pre_gradle_commands = (
            'python automation/taskcluster/helper/get-secret.py -s {} -k {} -f {}'.format(
                secret, key, target_file
            )
            for secret, key, target_file in (
                (sentry_secret, 'dsn', '.sentry_token'),
                (leanplum_secret, 'production', '.leanplum_token'),
            )
        )

        gradle_commands = (
            './gradlew --no-daemon -PcrashReports=true clean test assembleRelease',
        )

        command = ' && '.join(
            cmd
            for commands in (pre_gradle_commands, gradle_commands)
            for cmd in commands
            if cmd
        )

        routes = [] if is_staging else [
            "notify.email.fenix-eng-notifications@mozilla.com.on-failed"
        ]

        return self._craft_build_ish_task(
            name='Fenix - Build task',
            description='Build Fenix from source code',
            command=command,
            scopes=[
                "secrets:get:{}".format(secret) for secret in (sentry_secret, leanplum_secret)
            ],
            artifacts=artifacts,
            routes=routes,
            is_staging=is_staging,
        )

    def _craft_build_ish_task(
        self, name, description, command, dependencies=None, artifacts=None, scopes=None,
        routes=None, is_staging=True
    ):
        dependencies = [] if dependencies is None else dependencies
        artifacts = {} if artifacts is None else artifacts
        scopes = [] if scopes is None else scopes
        routes = [] if routes is None else routes

        checkout_command = (
            "export TERM=dumb && "
            "git fetch {} {} --tags && "
            "git config advice.detachedHead false && "
            "git checkout {}".format(
                self.repo_url, self.branch, self.commit
            )
        )

        command = '{} && {}'.format(checkout_command, command)

        features = {}
        if artifacts:
            features['chainOfTrust'] = True
        if any(scope.startswith('secrets:') for scope in scopes):
            features['taskclusterProxy'] = True

        payload = {
            "features": features,
            "maxRunTime": 7200,
            "image": "mozillamobile/fenix:1.3",
            "command": [
                "/bin/bash",
                "--login",
                "-cx",
                command
            ],
            "artifacts": artifacts,
        }

        return self._craft_default_task_definition(
            'mobile-1-b-fenix' if is_staging else 'mobile-3-b-fenix',
            'aws-provisioner-v1',
            dependencies,
            routes,
            scopes,
            name,
            description,
            payload
        )

    def _craft_default_task_definition(
        self, worker_type, provisioner_id, dependencies, routes, scopes, name, description, payload
    ):
        created = datetime.datetime.now()
        deadline = taskcluster.fromNow('1 day')
        expires = taskcluster.fromNow(DEFAULT_EXPIRES_IN)

        return {
            "provisionerId": provisioner_id,
            "workerType": worker_type,
            "taskGroupId": self.task_id,
            "schedulerId": self.scheduler_id,
            "created": taskcluster.stringDate(created),
            "deadline": taskcluster.stringDate(deadline),
            "expires": taskcluster.stringDate(expires),
            "retries": 5,
            "tags": {},
            "priority": self.tasks_priority,
            "dependencies": [self.task_id] + dependencies,
            "requires": "all-completed",
            "routes": routes,
            "scopes": scopes,
            "payload": payload,
            "metadata": {
                "name": name,
                "description": description,
                "owner": self.owner,
                "source": self.source,
            },
        }

    def craft_signing_task(
        self, build_task_id, apks, date_string, is_staging=True,
    ):
        date = arrow.get(date_string)
        signing_format = 'autograph_apk'
        payload = {
            "upstreamArtifacts": [{
                "paths": apks,
                "formats": [signing_format],
                "taskId": build_task_id,
                "taskType": "build",
            }],
        }

        index_release = 'staging-signed-nightly' if is_staging else 'signed-nightly'
        routes = [
            "index.project.mobile.fenix.{}.nightly.{}.{}.{}.latest".format(
                index_release, date.year, date.month, date.day
            ),
            "index.project.mobile.fenix.{}.nightly.{}.{}.{}.revision.{}".format(
                index_release, date.year, date.month, date.day, self.commit
            ),
            "index.project.mobile.fenix.{}.nightly.latest".format(index_release),
        ]

        return self._craft_default_task_definition(
            worker_type='mobile-signing-dep-v1' if is_staging else 'mobile-signing-v1',
            provisioner_id='scriptworker-prov-v1',
            dependencies=[build_task_id],
            routes=routes,
            scopes=[
                "project:mobile:fenix:releng:signing:format:{}".format(signing_format),
                "project:mobile:fenix:releng:signing:cert:{}".format(
                    'dep-signing' if is_staging else 'release-signing'
                )
            ],
            name="Fenix - Signing task",
            description="Sign release builds of Fenix",
            payload=payload
        )

    def craft_push_task(
        self, signing_task_id, apks, is_staging=True, commit=False
    ):
        payload = {
            "commit": commit,
            "google_play_track": 'nightly',
            "upstreamArtifacts": [
                {
                    "paths": apks,
                    "taskId": signing_task_id,
                    "taskType": "signing"
                }
            ]
        }

        return self._craft_default_task_definition(
            worker_type='mobile-pushapk-dep-v1' if is_staging else 'mobile-pushapk-v1',
            provisioner_id='scriptworker-prov-v1',
            dependencies=[signing_task_id],
            routes=[],
            scopes=[
                "project:mobile:fenix:releng:googleplay:product:fenix{}".format(
                    ':dep' if is_staging else ''
                )
            ],
            name="Fenix - Push task",
            description="Upload signed release builds of Fenix to Google Play",
            payload=payload
        )


def schedule_task(queue, taskId, task):
    print("TASK", taskId)
    print(json.dumps(task, indent=4, separators=(',', ': ')))

    result = queue.createTask(taskId, task)
    print("RESULT", taskId)
    print(json.dumps(result))


def schedule_task_graph(ordered_groups_of_tasks):
    queue = taskcluster.Queue({'baseUrl': 'http://taskcluster/queue/v1'})
    full_task_graph = {}

    # TODO: Switch to async python to speed up submission
    for group_of_tasks in ordered_groups_of_tasks:
        for task_id, task_definition in group_of_tasks.items():
            schedule_task(queue, task_id, task_definition)

            full_task_graph[task_id] = {
                # Some values of the task definition are automatically filled. Querying the task
                # allows to have the full definition. This is needed to make Chain of Trust happy
                'task': queue.task(task_id),
            }
    return full_task_graph
