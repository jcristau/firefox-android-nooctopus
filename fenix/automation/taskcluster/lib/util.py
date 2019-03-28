import json
import re


def convert_camel_case_into_kebab_case(string):
    # Inspired from https://stackoverflow.com/questions/1175208/elegant-python-function-to-convert-camelcase-to-snake-case  # noqa: E501
    first_pass = re.sub('(.)([A-Z][a-z]+)', r'\1-\2', string)
    return re.sub('([a-z0-9])([A-Z])', r'\1-\2', first_pass).lower()


def populate_chain_of_trust_required_but_unused_files():
    # Thoses files are needed to keep chainOfTrust happy. However, they have no
    # need for android-components, at the moment. For more details, see:
    # https://github.com/mozilla-releng/scriptworker/pull/209/files#r184180585

    for file_names in ('actions.json', 'parameters.yml'):
        with open(file_names, 'w') as f:
            json.dump({}, f)    # Yaml is a super-set of JSON.


def populate_chain_of_trust_task_graph(full_task_graph):
    # taskgraph must follow the format:
    # {
    #    task_id: full_task_definition
    # }
    with open('task-graph.json', 'w') as f:
        json.dump(full_task_graph, f)
