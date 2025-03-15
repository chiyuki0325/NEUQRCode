import config from "./config.js"
import express from "express"
import bodyParser from "body-parser"
import child_process from "child_process"

const app = express()
app.use(bodyParser.json())

const cqhttp = async (api, body) => {
  return await fetch(
    `http://${config.cqhttp.host}:${config.cqhttp.port}/${api}`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
    }
  )
}

const botMiddleware = (req, res, next) => {
  /*
  if (req.method === "POST") {
      console.log(req.body)
  }
      */
  res.send("")
}

app.use(botMiddleware)

app.listen(config.port, () => {
  console.log(`Bot is running on port ${config.port}`)
})

const webhook = express()
webhook.use(bodyParser.json())

webhook.post(config.webhook.endpoint, async (req, res) => {
  const body = req.body
  if ('commits' in body && "ref" in body && !body.ref.endsWith("beta")) {
    // new commit
    if (body.commits.length === 0) {
      return res.send("OK")
    }
    console.log(`New commit in ${body.repository.full_name}`)
    const message = "✨ " + ${body.ref.replace('refs/heads/', '')} + " 分支有新的提交：\n" + body.commits.map(commit => `  - ${commit.message}`).join("\n")
    await cqhttp("send_group_msg", {
      group_id: config.group_id,
      message: [{
        type: "text",
        data: {
          text: message
        }
      }]
    })
  } else if ('release' in body && body.action === 'published') {
    // new release
    let release_mode = true
    if (body.release.tag_name === "beta") {
      //skip ci builds
      release_mode = false
    }
    console.log(`New release in ${body.repository.full_name}`)
    let message = "";
    if (release_mode) {
      message = " 一码通 App 发布了新的版本：\n" + body.release.name + "\n" + body.release.body
    } else {
      message = "一码通 App 发布了新的测试版本。\n"
    }
    const release_assets_api_url = body.release.assets_url
    const release_assets_api_response = await fetch(release_assets_api_url)
    const release_assets = await release_assets_api_response.json()
    const release = release_assets.filter(asset => asset.name.startsWith("ink.chyk") && asset.name.endsWith(".apk"))[0]
    const url = release.browser_download_url
    // download release with curl
    if (release_mode) {
      child_process.execSync(`curl -Lo /tmp/${release.name} https://ghfast.top/${url}`)
      // send file
      await cqhttp("send_group_msg", {
        group_id: config.group_id,
        message: [{
          type: "file",
          data: {
            file: `/tmp/${release.name}`
          }
        }]
      })

      await cqhttp("send_group_msg", {
          group_id: config.group_id,
          message: [
            {
              type: "text",
              data: {
                text: "🚀 "
              }
            },
            {
              type: "at",
              data: {
                qq: "all"
              }
            },
            {
              type: "text",
              data: {
                text: message.trim()
              }
            }]
        }
      )
    } else {
      message += "\n" + url
      await cqhttp("send_group_msg", {
        group_id: config.group_id,
        message: [
          {
            type: "text",
            data: {text: "🆖 "}
          },
          {
            type: "text",
            data: {
              text: message
            }
          }
        ]
      })
    }
  } else if ('starred_at' in body) {
    if (body.action === 'created') {
      console.log(`New star in ${body.repository.full_name}`)
      const message = `🌟 @${body.sender.login} 给仓库点了 star！`
      await cqhttp("send_group_msg", {
        group_id: config.group_id,
        message: [{
          type: "text",
          data: {
            text: message
          }
        }]
      })
    } else if (body.action === 'deleted') {
      console.log(`Unstar in ${body.repository.full_name}`)
      const message = `💔 @${body.sender.login} 取消了 star...`
      await cqhttp("send_group_msg", {
        group_id: config.group_id,
        message: [{
          type: "text",
          data: {
            text: message
          }
        }]
      })
    }
  } else {
    console.log(body)
  }
  res.send("OK")
})

webhook.listen(config.webhook.port, () => {
  console.log(`Webhook is running on port ${config.webhook.port}`)
})
