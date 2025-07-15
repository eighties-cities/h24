import html
import os

from PythonSed import Sed, SedException

# based on https://github.com/mkdocs/mkdocs/issues/692
def define_env(env):
    "Hook function"

    @env.macro
    def code_from_file(fn: str, lineStart: int, lineEnd: int, flavor: str = ""):
        """
        Load code from a file and save as a preformatted code block.
        If a flavor is specified, it's passed in as a hint for syntax highlighters.

        Example usage in markdown:

            {{code_from_file("code/myfile.py", "python")}}

        """
        docs_dir = env.variables.get("docs_dir", "docs")
        fn = os.path.abspath(os.path.join(docs_dir, fn))

        sed = Sed()
        try:
            sed.no_autoprint = True
            sed.regexp_extended = False
            sed.load_string('{0},{1}p;'.format(lineStart, lineEnd))
            if not os.path.exists(fn):
                return f"""<b>File not found: {fn}</b>"""
            else:
                result = sed.apply(fn)
                return (
                    f"```{flavor} \n{''.join(result)}\n```"
                )
        except SedException as e:
            print(e.message)
        except:
            raise
    #
    #     # sed -n '16224,16482p;16483q'
    #
    # @env.macro
    # def external_markdown(fn: str):
    #     """
    #     Load markdown from files external to the mkdocs root path.
    #     Example usage in markdown:
    #
    #         {{external_markdown("../../README.md")}}
    #
    #     """
    #     docs_dir = env.variables.get("docs_dir", "docs")
    #     fn = os.path.abspath(os.path.join(docs_dir, fn))
    #     if not os.path.exists(fn):
    #         return f"""<b>File not found: {fn}</b>"""
    #     with open(fn, "r") as f:
    #         return f.read()
