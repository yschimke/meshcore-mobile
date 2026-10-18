"""Check the repository's local and mention-triggered MCP tool permission contract."""
import fnmatch
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
ALLOW = json.loads((ROOT / '.claude/settings.json').read_text())['permissions']['allow']


class AgentPermissionsTest(unittest.TestCase):
    def test_figma_names_match_the_supported_servers(self):
        for tool in ['mcp__figma__get_design_context', 'mcp__figma-desktop__get_design_context']:
            self.assertTrue(any(fnmatch.fnmatchcase(tool, rule) for rule in ALLOW), tool)
        self.assertNotIn('mcp__Figma', ALLOW)

    def test_tag_mode_receives_the_declared_github_tools(self):
        workflow = (ROOT / '.github/workflows/claude.yml').read_text()
        matches = re.findall(r'--allowedTools "([^"\n]+)"', workflow)
        self.assertEqual(len(matches), 1)
        action_allow = set(matches[0].split(','))
        declared = {rule for rule in ALLOW if rule.startswith('mcp__github__')}
        self.assertGreater(len(declared), 0)
        self.assertLessEqual(declared, action_allow)


if __name__ == '__main__':
    unittest.main()
